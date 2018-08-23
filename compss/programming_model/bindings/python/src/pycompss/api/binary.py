#!/usr/bin/python
#
#  Copyright 2002-2018 Barcelona Supercomputing Center (www.bsc.es)
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
PyCOMPSs API - BINARY
==================
    This file contains the class constraint, needed for the binary task
    definition through the decorator.
"""

import inspect
import logging
import os
from functools import wraps
from pycompss.util.location import at_master
from pycompss.util.location import i_am_within_scope

if __debug__:
    logger = logging.getLogger(__name__)


class Binary(object):
    """
    This decorator also preserves the argspec, but includes the __init__ and
    __call__ methods, useful on mpi task creation.
    """

    def __init__(self, *args, **kwargs):
        """
        Store arguments passed to the decorator
        # self = itself.
        # args = not used.
        # kwargs = dictionary with the given constraints.

        :param args: Arguments
        :param kwargs: Keyword arguments
        """

        self.args = args
        self.kwargs = kwargs
        self.scope = i_am_within_scope()
        if self.scope and __debug__:
            logger.debug("Init @binary decorator...")

    def __call__(self, func):
        """
        Parse and set the binary parameters within the task core element.

        :param func: Function to decorate
        :return: Decorated function.
        """

        if not self.scope:
            # from pycompss.api.dummy.binary import binary as dummy_binary
            # d_b = dummy_binary(self.args, self.kwargs)
            # return d_b.__call__(func)
            raise Exception("The binary decorator only works within PyCOMPSs framework.")

        if at_master():
            # master code
            from pycompss.runtime.binding import register_ce

            mod = inspect.getmodule(func)
            self.module = mod.__name__  # not func.__module__

            if (self.module == '__main__' or
                    self.module == 'pycompss.runtime.launch'):
                # The module where the function is defined was run as __main__,
                # we need to find out the real module name.

                # path=mod.__file__
                # dirs=mod.__file__.split(os.sep)
                # file_name=os.path.splitext(os.path.basename(mod.__file__))[0]

                # Get the real module name from our launch.py variable
                path = getattr(mod, "app_path")

                dirs = path.split(os.path.sep)
                file_name = os.path.splitext(os.path.basename(path))[0]
                mod_name = file_name

                i = len(dirs) - 1
                while i > 0:
                    new_l = len(path) - (len(dirs[i]) + 1)
                    path = path[0:new_l]
                    if "__init__.py" in os.listdir(path):
                        # directory is a package
                        i -= 1
                        mod_name = dirs[i] + '.' + mod_name
                    else:
                        break
                self.module = mod_name

            # Include the registering info related to @binary

            # Retrieve the base core_element established at @task decorator
            core_element = func.__to_register__
            # Update the core element information with the mpi information
            core_element.set_impl_type("BINARY")
            _binary = self.kwargs['binary']
            if 'workingDir' in self.kwargs:
                working_dir = self.kwargs['workingDir']
            else:
                working_dir = '[unassigned]'   # Empty or '[unassigned]'
            impl_signature = 'BINARY.' + _binary
            core_element.set_impl_signature(impl_signature)
            impl_args = [_binary, working_dir]
            core_element.set_impl_type_args(impl_args)
            func.__to_register__ = core_element
            # Do the task register if I am the top decorator
            if func.__who_registers__ == __name__:
                if __debug__:
                    logger.debug("[@BINARY] I have to do the register of function %s in module %s" % (func.__name__, self.module))
                register_ce(core_element)
        else:
            # worker code
            pass

        @wraps(func)
        def binary_f(*args, **kwargs):
            # This is executed only when called.
            if __debug__:
                logger.debug("Executing binary_f wrapper.")

            if len(args) > 0:
                # The 'self' for a method function is passed as args[0]
                slf = args[0]

                # Replace and store the attributes
                saved = {}
                for k, v in self.kwargs.items():
                    if hasattr(slf, k):
                        saved[k] = getattr(slf, k)
                        setattr(slf, k, v)

            # Call the method
            ret = func(*args, **kwargs)

            if len(args) > 0:
                # Put things back
                for k, v in saved.items():
                    setattr(slf, k, v)

            return ret

        binary_f.__doc__ = func.__doc__
        return binary_f


# ############################################################################# #
# #################### BINARY DECORATOR ALTERNATIVE NAME ###################### #
# ############################################################################# #

binary = Binary
