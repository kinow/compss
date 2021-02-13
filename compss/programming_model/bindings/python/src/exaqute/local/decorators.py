
from exaqute.common import ExaquteException
from .internals import _check_init, ValueWrapper, _obj_to_value
from .consts import IN, FILE_IN, FILE_OUT, INOUT, FILE_INOUT, COLLECTION_IN, COLLECTION_OUT, COLLECTION_INOUT
import traceback
from functools import wraps
import os

class Task(object):
    """
    Dummy task class (decorator style)
    """

    def __init__(self, *args, **kwargs):
        self.args = args
        self.returns=None
        self.priority=False
        if 'returns' in kwargs:
            self.returns = kwargs.pop('returns')
        if 'priority' in kwargs:
            self.priority = kwargs.pop('priority')
        for k, v in kwargs.items():
            if v not in (IN, INOUT, FILE_IN, FILE_OUT, FILE_INOUT, COLLECTION_IN, COLLECTION_OUT, COLLECTION_INOUT):
                if not isinstance(v,dict):
                    raise ExaquteException("Argument '{}' has invalid value".format(k))


    def __call__(self, f):
        @wraps(f)
        def wrapped_f(*args, **kwargs):
            _check_init()
            if 'returns' in kwargs:
                returns = kwargs.pop('returns')
            else:
                returns = self.returns
            if 'keep' in kwargs:
                keep = kwargs.pop('keep')
            else:
                keep = False
            for obj in args:
                _obj_to_value(obj)
            for k,v in kwargs:
                _obj_to_value(v)
            if returns is not None:
                result = f(*args, **kwargs)
                if returns != 1:
                    if not hasattr(result, "__len__") or len(result) != returns:
                        raise ExaquteException("Invalid number of results returned (expected {})".format(returns))
                    return [ValueWrapper(r, keep) for r in result]
                else:
                    return ValueWrapper(result, keep)
            else:
                f(*args, **kwargs)
       
        return wrapped_f


task = Task

def constraint(computing_units=1):
    if isinstance(computing_units, str):
        try:
            #can be cast to int
            computing_units=int(computing_units)
        except:
            if computing_units.startswith("$"):
                env_var=computing_units.replace("$","").replace("{","").replace("}","")
                computing_units=int(os.environ[env_var])
    if (not isinstance(computing_units, int) and computing_units > 0):
        raise ExaquteException("incorrect computing_units")
    return lambda x: x

class Mpi(object):

    def __init__(self, *args, **kwargs):
        self.processes=1
        if 'processes' in kwargs:
            self.processes = kwargs['processes']
        else: 
            raise ExaquteException("Number of processes not specified")
        if not 'runner' in kwargs:
            raise ExaquteException("Runner must be specified")
        for k, v in kwargs.items():
            if k not in ('processes', 'runner', 'flags'):
                if not k.endswith("_layout"):
                    raise ExaquteException("Argument '{}' is not valid".format(k))

    def __call__(self, f):
        def wrapped_f(*args, **kwargs):
            _check_init()
            if self.processes == 1:
                return [f(*args, **kwargs)]
            else:
                return f(*args, **kwargs)
        return wrapped_f

mpi = Mpi