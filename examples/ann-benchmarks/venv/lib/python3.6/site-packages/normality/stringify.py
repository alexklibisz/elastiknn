import six
from datetime import datetime, date
from decimal import Decimal

from normality.cleaning import remove_unsafe_chars
from normality.encoding import guess_encoding
from normality.encoding import DEFAULT_ENCODING


def stringify(value, encoding_default=DEFAULT_ENCODING, encoding=None):
    """Brute-force convert a given object to a string.

    This will attempt an increasingly mean set of conversions to make a given
    object into a unicode string. It is guaranteed to either return unicode or
    None, if all conversions failed (or the value is indeed empty).
    """
    if value is None:
        return None

    if not isinstance(value, six.text_type):
        if isinstance(value, (date, datetime)):
            return value.isoformat()
        elif isinstance(value, (float, Decimal)):
            return Decimal(value).to_eng_string()
        elif isinstance(value, six.binary_type):
            if encoding is None:
                encoding = guess_encoding(value, default=encoding_default)
            value = value.decode(encoding, 'replace')
            value = remove_unsafe_chars(value)
        else:
            value = six.text_type(value)

    # XXX: is this really a good idea?
    value = value.strip()
    if not len(value):
        return None
    return value
