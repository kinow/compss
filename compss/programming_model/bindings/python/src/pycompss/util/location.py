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
PyCOMPSs Utils - Location
===================
    This file contains the methods to detect the origin of the call stack.
    Useful to detect if we are in the master or in the worker.
"""

_WHERE = 'OUTOFSCOPE'


def at_master():
    """
    Determine if the execution is being performed in the master node
    :return: Boolean
    """
    return _WHERE == 'MASTER'


def at_worker():
    """
    Determine if the execution is being performed in a worker node.
    :return: Boolean
    """
    return _WHERE == 'WORKER'


def during_initialization():
    """
    Determine if the execution is in the initialization stage.
    :return: Boolean
    """
    return _WHERE == 'INITIALIZATION'


def set_pycompss_context(where):
    """
    Set the Python Binding context (MASTER or WORKER or INITIALIZATION)
    :param where: New context (MASTER or WORKER or INITIALIZATION)
    :return: None
    """
    assert where in ['MASTER', 'WORKER', 'INITIALIZATION'], 'PyCOMPSs context should be MASTER|WORKER|INITIALIZATION'
    global _WHERE
    _WHERE = where


def i_am_within_scope():
    """
    Determine if the execution is being performed within the PyCOMPSs scope.
    :return:  <Boolean> - True if under scope. False on the contrary.
    """
    return _WHERE != 'OUTOFSCOPE'
