#!/usr/bin/python
#
#  Copyright 2002-2019 Barcelona Supercomputing Center (www.bsc.es)
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
PyCOMPSs runtime - Task - Keys
============================
    This file contains the task keys.
"""


class ParamAliasKeys(object):
    """
    Strings used in Tasks definition
    """
    IN = 'IN'
    OUT = 'OUT'
    INOUT = 'INOUT'
    CONCURRENT = 'CONCURRENT'
    COMMUTATIVE = 'COMMUTATIVE'

    FILE = 'FILE'
    FILE_IN = 'FILE_IN'
    FILE_OUT = 'FILE_OUT'
    FILE_INOUT = 'FILE_INOUT'
    FILE_CONCURRENT = 'FILE_CONCURRENT'
    FILE_COMMUTATIVE = 'FILE_COMMUTATIVE'

    FILE_STDIN = 'FILE_STDIN'
    FILE_STDERR = 'FILE_STDERR'
    FILE_STDOUT = 'FILE_STDOUT'

    FILE_IN_STDIN = 'FILE_IN_STDIN'
    FILE_IN_STDERR = 'FILE_IN_STDERR'
    FILE_IN_STDOUT = 'FILE_IN_STDOUT'
    FILE_OUT_STDIN = 'FILE_OUT_STDIN'
    FILE_OUT_STDERR = 'FILE_OUT_STDERR'
    FILE_OUT_STDOUT = 'FILE_OUT_STDOUT'
    FILE_INOUT_STDIN = 'FILE_INOUT_STDIN'
    FILE_INOUT_STDERR = 'FILE_INOUT_STDERR'
    FILE_INOUT_STDOUT = 'FILE_INOUT_STDOUT'
    FILE_CONCURRENT_STDIN = 'FILE_CONCURRENT_STDIN'
    FILE_CONCURRENT_STDERR = 'FILE_CONCURRENT_STDERR'
    FILE_CONCURRENT_STDOUT = 'FILE_CONCURRENT_STDOUT'
    FILE_COMMUTATIVE_STDIN = 'FILE_COMMUTATIVE_STDIN'
    FILE_COMMUTATIVE_STDERR = 'FILE_COMMUTATIVE_STDERR'
    FILE_COMMUTATIVE_STDOUT = 'FILE_COMMUTATIVE_STDOUT'

    DIRECTORY = 'DIRECTORY'
    DIRECTORY_IN = 'DIRECTORY_IN'
    DIRECTORY_OUT = 'DIRECTORY_OUT'
    DIRECTORY_INOUT = 'DIRECTORY_INOUT'

    COLLECTION = 'COLLECTION'
    COLLECTION_IN = 'COLLECTION_IN'
    COLLECTION_INOUT = 'COLLECTION_INOUT'
    COLLECTION_OUT = 'COLLECTION_OUT'
    COLLECTION_FILE = 'COLLECTION_FILE'
    COLLECTION_FILE_IN = 'COLLECTION_FILE_IN'
    COLLECTION_FILE_INOUT = 'COLLECTION_FILE_INOUT'
    COLLECTION_FILE_OUT = 'COLLECTION_FILE_OUT'

    STREAM_IN = 'STREAM_IN'
    STREAM_OUT = 'STREAM_OUT'


class ParamDictKeys(object):
    """
    Strings used in Parameter definition as dictionary
    """
    # Exposed to the user (see api/parameter.py)
    Type = 'type'
    Direction = 'direction'
    StdIOStream = 'stream'
    Prefix = 'prefix'
    Depth = 'depth'
    Weight = 'weight'
    Keep_rename = 'keep_rename'
    # Private (see task/parameter.py)
    Content_type = 'content_type'
    Is_file_collection = 'is_file_collection'