import six

BOOL_TRUEISH = ['1', 'yes', 'y', 't', 'true', 'on', 'enabled']


def as_bool(value, default=False):
    if isinstance(value, bool):
        return value
    if value is None:
        return default
    value = six.text_type(value)
    value = value.strip().lower()
    if not len(value):
        return default
    return value in BOOL_TRUEISH
