package com.mw.offlineupi.util

/**
 * A [CharSequence] view over a [CharArray], so a UPI PIN can be held in wipeable storage.
 *
 * Kotlin's `String(chars)` and `chars.concatToString()` create an immutable copy that cannot be
 * wiped and lives until GC decides otherwise. Wrapping the array means the PIN has exactly one
 * long-lived representation, and wiping the array wipes it.
 *
 * See [toString] for the one place a copy is unavoidable, and why.
 */
class CharArrayCharSequence(
    private val chars: CharArray,
    override val length: Int = chars.size
) : CharSequence {

    override fun get(index: Int): Char {
        if (index < 0 || index >= length) throw IndexOutOfBoundsException("index=$index len=$length")
        return chars[index]
    }

    override fun subSequence(startIndex: Int, endIndex: Int): CharSequence {
        if (startIndex < 0 || endIndex > length || startIndex > endIndex) {
            throw IndexOutOfBoundsException("start=$startIndex end=$endIndex len=$length")
        }
        return CharArrayCharSequence(chars.copyOfRange(startIndex, endIndex))
    }

    /**
     * Returns a `String` copy of the contents.
     *
     * This previously threw, to stop a stray string template from silently copying the PIN. That
     * was wrong, and it crashed the app the instant a PIN was submitted.
     *
     * Handing text to the USSD dialog crosses a Binder boundary into another process.
     * `AccessibilityNodeInfo.performAction` parcels its argument Bundle, and `Parcel.writeValue`
     * routes every `CharSequence` that is not already a `String` through
     * `TextUtils.writeToParcel`, which calls `toString()`. `ClipData` does the same in the paste
     * fallback. No API moves characters into another process's EditText without materialising a
     * `String` first, so throwing here did not prevent the copy — it only raised
     * `UnsupportedOperationException` from inside `Parcel.writeValue`, on the main thread.
     *
     * What the class still buys is real: the `String` produced here is a temporary that lives for
     * one parcel write and is immediately garbage, rather than a value held for the length of the
     * session. [chars] remains the caller's to wipe, and wiping it stays worthwhile.
     */
    override fun toString(): String = String(chars, 0, length)
}
