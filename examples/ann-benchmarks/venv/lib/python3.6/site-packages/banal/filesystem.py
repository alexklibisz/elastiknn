import six
import sys


def decode_path(file_path):
    """Turn a path name into unicode."""
    if file_path is None:
        return
    if isinstance(file_path, six.binary_type):
        file_path = file_path.decode(sys.getfilesystemencoding())
    return file_path
