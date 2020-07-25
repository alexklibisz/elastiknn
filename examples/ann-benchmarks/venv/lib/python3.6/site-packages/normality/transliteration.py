# coding: utf-8
"""
Transliterate the given text to the latin script.

This attempts to convert a given text to latin script using the
closest match of characters vis a vis the original script.

Transliteration requires an extensive unicode mapping. Since all
Python implementations are either GPL-licensed (and thus more
restrictive than this library) or come with a massive C code
dependency, this module requires neither but will use a package
if it is installed.
"""
import warnings
from normality.cleaning import compose_nfkc, is_text

# Transform to latin, separate accents, decompose, remove
# symbols, compose, push to ASCII
ASCII_SCRIPT = 'Any-Latin; NFKD; [:Symbol:] Remove; [:Nonspacing Mark:] Remove; NFKC; Accents-Any; Latin-ASCII'  # noqa


class ICUWarning(UnicodeWarning):
    pass


def latinize_text(text, ascii=False):
    """Transliterate the given text to the latin script.

    This attempts to convert a given text to latin script using the
    closest match of characters vis a vis the original script.
    """
    if text is None or not is_text(text) or not len(text):
        return text

    if ascii:
        if not hasattr(latinize_text, '_ascii'):
            latinize_text._ascii = make_transliterator(ASCII_SCRIPT)
        return latinize_text._ascii(text)

    if not hasattr(latinize_text, '_tr'):
        latinize_text._tr = make_transliterator('Any-Latin')
    return latinize_text._tr(text)


def ascii_text(text):
    """Transliterate the given text and make sure it ends up as ASCII."""
    text = latinize_text(text, ascii=True)
    if is_text(text):
        return text.encode('ascii', 'ignore').decode('ascii')


def make_transliterator(script):
    try:
        from icu import Transliterator
        inst = Transliterator.createInstance(script)
        return inst.transliterate
    except ImportError:
        from text_unidecode import unidecode
        warnings.warn("Install 'pyicu' for better text transliteration.", ICUWarning, stacklevel=4)  # noqa

        def transliterate(text):
            text = compose_nfkc(text)
            return unidecode(text)

        return transliterate
