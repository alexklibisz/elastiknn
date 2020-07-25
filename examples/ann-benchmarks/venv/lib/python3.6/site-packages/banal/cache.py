import six
import types
from itertools import chain
from hashlib import sha1
from datetime import date, datetime

from banal.dicts import is_mapping
from banal.lists import is_sequence


def bytes_iter(obj):
    """Turn a complex object into an iterator of byte strings.
    The resulting iterator can be used for caching.
    """
    if obj is None:
        return
    elif isinstance(obj, six.binary_type):
        yield obj
    elif isinstance(obj, six.string_types):
        yield obj
    elif isinstance(obj, (date, datetime)):
        yield obj.isoformat()
    elif is_mapping(obj):
        for key in sorted(obj.keys()):
            for out in chain(bytes_iter(key), bytes_iter(obj[key])):
                yield out
    elif is_sequence(obj):
        if isinstance(obj, (list, set)):
            try:
                obj = sorted(obj)
            except Exception:
                pass
        for item in obj:
            for out in bytes_iter(item):
                yield out
    elif isinstance(obj, (types.FunctionType, types.BuiltinFunctionType,
                          types.MethodType, types.BuiltinMethodType)):
        yield getattr(obj, 'func_name', '')
    else:
        yield six.text_type(obj)


def hash_data(obj):
    """Generate a SHA1 from a complex object."""
    collect = sha1()
    for text in bytes_iter(obj):
        if isinstance(text, six.text_type):
            text = text.encode('utf-8')
        collect.update(text)
    return collect.hexdigest()
