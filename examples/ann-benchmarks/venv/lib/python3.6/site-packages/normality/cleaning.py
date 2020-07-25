# coding: utf-8
from __future__ import unicode_literals

import re
import six
import unicodedata

from normality.constants import UNICODE_CATEGORIES, CONTROL_CODES, WS

COLLAPSE_RE = re.compile(r'\s+', re.U)
BOM_RE = re.compile('^\ufeff', re.U)
UNSAFE_RE = re.compile(r'^\ufeff|[\x00-\x08\x0b-\x0c\x0e-\x1f\x7f\x80-\x9f]')
QUOTES_RE = re.compile(r'^["\'](.*)["\']$')


def decompose_nfkd(text):
    """Perform unicode compatibility decomposition.

    This will replace some non-standard value representations in unicode and
    normalise them, while also separating characters and their diacritics into
    two separate codepoints.
    """
    if is_text(text):
        return unicodedata.normalize('NFKD', text)


def compose_nfc(text):
    """Perform unicode composition."""
    if is_text(text):
        return unicodedata.normalize('NFC', text)


def compose_nfkc(text):
    """Perform unicode composition."""
    if is_text(text):
        return unicodedata.normalize('NFKC', text)


def strip_quotes(text):
    """Remove double or single quotes surrounding a string."""
    if is_text(text):
        return QUOTES_RE.sub('\\1', text)


def category_replace(text, replacements=UNICODE_CATEGORIES):
    """Remove characters from a string based on unicode classes.

    This is a method for removing non-text characters (such as punctuation,
    whitespace, marks and diacritics) from a piece of text by class, rather
    than specifying them individually.
    """
    if not is_text(text):
        return None
    characters = []
    for character in decompose_nfkd(text):
        cat = unicodedata.category(character)
        replacement = replacements.get(cat, character)
        if replacement is not None:
            characters.append(replacement)
    return u''.join(characters)


def remove_control_chars(text):
    """Remove just the control codes from a piece of text."""
    return category_replace(text, replacements=CONTROL_CODES)


def remove_unsafe_chars(text):
    """Remove unsafe unicode characters from a piece of text."""
    if is_text(text):
        return UNSAFE_RE.sub('', text)


def remove_byte_order_mark(text):
    """Remove a BOM from the beginning of the text."""
    if is_text(text):
        return BOM_RE.sub('', text)


def collapse_spaces(text):
    """Remove newlines, tabs and multiple spaces with single spaces."""
    if is_text(text):
        return COLLAPSE_RE.sub(WS, text).strip(WS)


def is_text(data):
    return isinstance(data, six.text_type)
