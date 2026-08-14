package com.xayah.core.rootservice.parcelables

import android.os.Parcel
import android.os.Parcelable

class ArchiveExtractionParcelable() : Parcelable {
    var code: Int = -1
    var output: List<String> = emptyList()
    var skippedEntries: Int = 0
    var pendingLinks: Int = 0

    constructor(code: Int, output: List<String>, skippedEntries: Int, pendingLinks: Int) : this() {
        this.code = code
        this.output = output
        this.skippedEntries = skippedEntries
        this.pendingLinks = pendingLinks
    }

    constructor(parcel: Parcel) : this() {
        code = parcel.readInt()
        output = parcel.createStringArrayList().orEmpty()
        skippedEntries = parcel.readInt()
        pendingLinks = parcel.readInt()
    }

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeInt(code)
        parcel.writeStringList(output)
        parcel.writeInt(skippedEntries)
        parcel.writeInt(pendingLinks)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<ArchiveExtractionParcelable> {
        override fun createFromParcel(parcel: Parcel) = ArchiveExtractionParcelable(parcel)

        override fun newArray(size: Int): Array<ArchiveExtractionParcelable?> = arrayOfNulls(size)
    }
}
