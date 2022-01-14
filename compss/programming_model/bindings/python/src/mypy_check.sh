#!/bin/bash

############################
## Check typing with mypy ##
############################

/opt/python/bin/mypy --pretty --ignore-missing-imports --exclude 'pycompss\/((tests)\/|(dds)\/|(functions\/data_tasks.py)|(functions\/elapsed_time.py)|(streams\/)|(runtime\/launch.py)|(util\/translators\/code_replacer\/tests\/original.py)|(util\/interactive\/events.py)|(interactive.py)|(__main__.py))$' ./pycompss/
