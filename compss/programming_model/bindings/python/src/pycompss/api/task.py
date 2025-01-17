#!/usr/bin/python
#
#  Copyright 2002-2022 Barcelona Supercomputing Center (www.bsc.es)
#
#  Licensed under the Apache License, Version 2.0 (the "License");
#  you may not use this file except in compliance with the License.
#  You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
#  limitations under the License.
#

# -*- coding: utf-8 -*-

"""
PyCOMPSs API - Task decorator.

This file contains the Task class, needed for the task definition.
"""

from __future__ import print_function

import inspect
import os
import sys
from functools import wraps

from pycompss.api import parameter
from pycompss.util.context import CONTEXT
from pycompss.api.commons.constants import INTERNAL_LABELS
from pycompss.api.commons.decorator import CORE_ELEMENT_KEY
from pycompss.api.commons.implementation_types import IMPLEMENTATION_TYPES
from pycompss.api.dummy.task import task as dummy_task
from pycompss.runtime.binding import nested_barrier
from pycompss.runtime.start.initialization import LAUNCH_STATUS
from pycompss.runtime.task.core_element import CE
from pycompss.runtime.task.master import TaskMaster
from pycompss.runtime.task.parameter import get_new_parameter
from pycompss.runtime.task.parameter import get_parameter_from_dictionary
from pycompss.runtime.task.parameter import is_param
from pycompss.runtime.task.worker import TaskWorker
from pycompss.util.logger.helpers import update_logger_handlers
from pycompss.util.objects.properties import get_module_name
from pycompss.util.tracing.helpers import EventInsideWorker
from pycompss.util.tracing.helpers import EventMaster
from pycompss.util.tracing.types_events_master import TRACING_MASTER
from pycompss.util.tracing.types_events_worker import TRACING_WORKER
from pycompss.util.typing_helper import dummy_function
from pycompss.util.typing_helper import typing

if __debug__:
    import logging

    logger = logging.getLogger(__name__)


class Task:  # pylint: disable=too-few-public-methods, too-many-instance-attributes
    """This is the Task decorator implementation.

    It is implemented as a class and consequently this implementation can be
    divided into two natural steps: decoration process and function call.

    Decoration process is what happens when the Python parser reads a decorated
    function. The actual function is not called, but the @task() triggers
    the process that stores and processes the parameters of the decorator.
    This first step corresponds to the class constructor.

    Function call is what happens when the user calls their function somewhere
    in the code. A decorator simply adds pre and post steps in this function
    call, allowing us to change and process the arguments. This second steps
    happens in the __call__ implementation.

    Also, the call itself does different things in the master than in the
    worker. We must also handle the case when the user just runs the app with
    python and no PyCOMPSs.
    The specific implementations can be found in TaskMaster.call(),
    TaskWorker.call() and self._sequential_call()
    """

    __slots__ = [
        "task_type",
        "decorator_arguments",
        "user_function",
        "registered",
        "signature",
        "interactive",
        "module",
        "function_arguments",
        "function_name",
        "module_name",
        "function_type",
        "class_name",
        "hints",
        "on_failure",
        "defaults",
        "decorator_name",
        "args",
        "kwargs",
        "scope",
        "core_element",
        "core_element_configured",
    ]

    def __init__(self, *args: typing.Any, **kwargs: typing.Any) -> None:
        """Task constructor.

        This part is called in the decoration process, not as an
        explicit function call.

        We do two things here:
            a) Assign default values to unspecified fields.
            b) Transform the parameters from user friendly types
               (i.e Parameter.IN, etc) to a more convenient internal
               representation.

        :param args: Decorator positional parameters (ignored).
        :param kwargs: Decorator parameters. A task decorator has no positional
                       arguments.
        """
        self.task_type = IMPLEMENTATION_TYPES.method
        self.decorator_name = "".join(("@", Task.__name__.lower()))
        self.args = args
        self.kwargs = kwargs
        self.scope = CONTEXT.in_pycompss()
        self.core_element = CE()
        self.core_element_configured = False

        # Set missing values to their default ones (step a)
        self.decorator_arguments = kwargs
        default_decorator_values = {
            "target_direction": parameter.INOUT,
            "returns": False,
            "cache_returns": True,
            "priority": False,
            "defaults": {},
            "time_out": 0,
            "is_replicated": False,
            "is_distributed": False,
            "computing_nodes": 1,
            "is_reduce": False,
            "chunk_size": 0,
            "tracing_hook": False,
            "numba": False,  # numba mode (jit, vectorize, guvectorize)
            "numba_flags": {},  # user defined extra numba flags
            "numba_signature": None,  # vectorize and guvectorize signature
            "numba_declaration": None,  # guvectorize declaration
            "varargs_type": parameter.IN,  # Here for legacy purposes
        }  # type: typing.Dict[str, typing.Any]
        for (key, value) in default_decorator_values.items():
            if key not in self.decorator_arguments:
                self.decorator_arguments[key] = value
        # Give all parameters a unique instance for them (step b)
        # Note that when a user defines a task like
        # @task(a = IN, b = IN, c = INOUT)
        # both a and b point to the same IN object (the one from parameter.py)
        # Giving them a unique instance makes life easier in further steps
        for (key, value) in self.decorator_arguments.items():
            # Not all decorator arguments are necessarily parameters
            # (see default_decorator_values)
            if is_param(value):
                self.decorator_arguments[key] = get_new_parameter(value.key)
            # Specific case when value is a dictionary
            # Use case example:
            # @binary(binary="ls")
            # @task(hide={Type: FILE_IN, Prefix: "--hide="},
            #       sort={Type: IN, Prefix: "--sort="})
            # def myLs(flag, hide, sort):
            #   pass
            # Transform this dictionary to a Parameter object
            if isinstance(value, dict):
                if key not in [
                    "numba",
                    "numba_flags",
                    "numba_signature",
                    "numba_declaration",
                ]:
                    # Perform user -> instance substitution
                    # param = self.decorator_arguments[key][parameter.Type]
                    # Replace the whole dict by a single parameter object
                    self.decorator_arguments[key] = get_parameter_from_dictionary(
                        self.decorator_arguments[key]
                    )
                else:
                    # It is a reserved word that we need to keep the user
                    # defined value (not a Parameter object)
                    self.decorator_arguments[key] = value
        self.user_function = dummy_function  # type: typing.Callable
        # Global variables common for all tasks of this kind
        self.registered = False
        self.signature = ""
        # Saved from the initial task
        self.interactive = False
        self.module = None  # type: typing.Any
        self.function_arguments = tuple()  # type: tuple
        self.function_name = ""
        self.module_name = ""
        self.function_type = -1
        self.class_name = ""
        self.hints = tuple()  # type: tuple
        self.on_failure = ""
        self.defaults = {}  # type: dict

    def __call__(self, user_function: typing.Callable) -> typing.Callable:
        """Perform the task processing.

        This function is called in all explicit function calls.
        Note that in PyCOMPSs a single function call will be transformed into
        two calls, as both master and worker need to call the function.

        The work to do in the master part is totally different
        from the job to do in the worker part. This is why there are
        some other functions like master_call, worker_call, and
        _sequential_call.

        There is also a third case that happens when the user runs a PyCOMPSs
        code without PyCOMPSs. This case is straightforward: just call the
        user function with the user parameters and return whatever the user
        code returned. Therefore, we can just return the user function.

        :param user_function: Function to decorate.
        :return: The function to be executed.
        """
        self.user_function = user_function

        @wraps(user_function)
        def task_decorator(*args: typing.Any, **kwargs: typing.Any) -> typing.Any:
            return self.__decorator_body__(user_function, args, kwargs)

        return task_decorator

    def __decorator_body__(
        self, user_function: typing.Callable, args: tuple, kwargs: dict
    ) -> typing.Any:
        """Body of the task decorator.

        :param user_function: Decorated function.
        :param args: Function arguments.
        :param kwargs: Function keyword arguments.
        :returns: Result of executing the user_function with the given args and kwargs.
        """
        # Determine the context and decide what to do
        if CONTEXT.in_master():
            # @task being executed in the master
            # Each task will have a TaskMaster, so its content will
            # not be shared.
            self.__check_core_element__(kwargs, user_function)
            with EventMaster(TRACING_MASTER.task_instantiation):
                master = TaskMaster(
                    self.decorator_arguments,
                    self.user_function,
                    self.core_element,
                    self.registered,
                    self.signature,
                    self.interactive,
                    self.module,
                    self.function_arguments,
                    self.function_name,
                    self.module_name,
                    self.function_type,
                    self.class_name,
                    self.hints,
                    self.on_failure,
                    self.defaults,
                )
            result = master.call(args, kwargs)
            (
                future_object,
                self.core_element,
                self.registered,
                self.signature,
                self.interactive,
                self.module,
                self.function_arguments,
                self.function_name,
                self.module_name,
                self.function_type,
                self.class_name,
                self.hints,
            ) = result
            del master
            return future_object
        if CONTEXT.in_worker():
            if "compss_key" in kwargs.keys():
                is_nesting_enabled = CONTEXT.is_nesting_enabled()
                if is_nesting_enabled:
                    if __debug__:
                        # Update the whole logger since it will be in job out/err
                        update_logger_handlers(
                            kwargs["compss_log_cfg"],
                            kwargs["compss_log_files"][0],
                            kwargs["compss_log_files"][1],
                        )
                # @task being executed in the worker
                with EventInsideWorker(TRACING_WORKER.worker_task_instantiation):
                    worker = TaskWorker(
                        self.decorator_arguments,
                        self.user_function,
                        self.on_failure,
                        self.defaults,
                    )
                result = worker.call(*args, **kwargs)
                # Force flush stdout and stderr
                sys.stdout.flush()
                sys.stderr.flush()
                # Remove worker
                del worker
                if is_nesting_enabled:
                    # Wait for all nested tasks to finish
                    nested_barrier()
                    if __debug__:
                        # Reestablish logger handlers
                        update_logger_handlers(kwargs["compss_log_cfg"])
                return result

            # There is no compss_key in kwargs.keys() => task invocation within task:
            #  - submit the task to the runtime if nesting is enabled.
            #  - execute sequentially if nested is not enabled.
            if CONTEXT.is_nesting_enabled():
                # Each task will have a TaskMaster, so its content will
                # not be shared.
                with EventMaster(TRACING_MASTER.task_instantiation):
                    master = TaskMaster(
                        self.decorator_arguments,
                        self.user_function,
                        self.core_element,
                        self.registered,
                        self.signature,
                        self.interactive,
                        self.module,
                        self.function_arguments,
                        self.function_name,
                        self.module_name,
                        self.function_type,
                        self.class_name,
                        self.hints,
                        self.on_failure,
                        self.defaults,
                    )
                result = master.call(args, kwargs)
                (
                    future_object,
                    self.core_element,
                    self.registered,
                    self.signature,
                    self.interactive,
                    self.module,
                    self.function_arguments,
                    self.function_name,
                    self.module_name,
                    self.function_type,
                    self.class_name,
                    self.hints,
                ) = result
                del master
                return future_object
            # Called from another task within the worker
            # Ignore the @task decorator and run it sequentially
            message = (
                f"WARNING: Calling task: {str(user_function.__name__)} from this task.\n"
                f"         It will be executed sequentially within the caller task."
            )
            print(message, file=sys.stderr)
            return self._sequential_call(*args, **kwargs)
        # We are neither in master nor in the worker, or the user has
        # stopped the interactive session.
        # Therefore, the user code is being executed with no
        # launch_compss/enqueue_compss/runcompss/interactive session
        return self._sequential_call(*args, **kwargs)

    def _sequential_call(self, *args: typing.Any, **kwargs: typing.Any) -> typing.Any:
        """Sequential task execution.

        The easiest case: just call the user function and return whatever it
        returns.

        :return: The user function return.
        """
        # Inspect the user function, get information about the arguments and
        # their names
        # This defines self.param_args, self.param_varargs,
        # self.param_kwargs and self.param_defaults
        # And gives non-None default values to them if necessary
        d_t = dummy_task(args, kwargs)
        return d_t.__call__(self.user_function)(*args, **kwargs)

    def __check_core_element__(
        self, kwargs: dict, user_function: typing.Callable
    ) -> None:
        """Check Core Element for containers.

        :param kwargs: Keyword arguments.
        :param user_function: User function.
        :return: None (updates the Core Element of the given kwargs).
        """
        if (
            CORE_ELEMENT_KEY in kwargs
            and kwargs[CORE_ELEMENT_KEY].get_impl_type()
            == IMPLEMENTATION_TYPES.container
        ):
            # The task is using a container
            impl_args = kwargs[CORE_ELEMENT_KEY].get_impl_type_args()
            _type = impl_args[2]
            if _type == INTERNAL_LABELS.unassigned:
                # The task is not invoking a binary
                _engine = impl_args[0]
                _image = impl_args[1]
                _type = "CET_PYTHON"
                _module_name = str(self.__get_module_name__(user_function))
                _function_name = str(user_function.__name__)
                _func_complete = f"{_module_name}&{_function_name}"
                impl_args = [
                    _engine,  # engine
                    _image,  # image
                    _type,  # internal_type
                    INTERNAL_LABELS.unassigned,  # internal_binary
                    _func_complete,  # internal_func
                    INTERNAL_LABELS.unassigned,  # working_dir
                    INTERNAL_LABELS.unassigned,
                ]  # fail_by_ev
                kwargs[CORE_ELEMENT_KEY].set_impl_type_args(impl_args)

    @staticmethod
    def __get_module_name__(user_function: typing.Callable) -> str:
        """Get the module name from the user function.

        :param user_function: User function.
        :returns: The module name where the user function is defined.
        """
        mod = inspect.getmodule(user_function)  # type: typing.Any
        module_name = mod.__name__
        if module_name == "__main__":
            # The module where the function is defined was run as __main__,
            # We need to find out the real module name
            # Get the real module name from our launch.py APP_PATH global
            # variable
            # It is guaranteed that this variable will always exist because
            # this code is only executed when we know we are in the master
            path = LAUNCH_STATUS.get_app_path()
            # Get the file name
            file_name = os.path.splitext(os.path.basename(path))[0]
            # Get the module
            module_name = get_module_name(path, file_name)
        return module_name


# task can be also typed as Task
task = Task  # pylint: disable=invalid-name
