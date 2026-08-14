package com.xayah.core.rootservice.parcelables

import android.os.Parcel
import android.os.Parcelable

class DirectoryListingParcelable() : Parcelable {
    var successful: Boolean = false
    var paths: List<String> = emptyList()

    constructor(successful: Boolean, paths: List<String>) : this() {
        this.successful = successful
        this.paths = paths
    }

    constructor(parcel: Parcel) : this() {
        successful = parcel.readByte() != 0.toByte()
        paths = parcel.createStringArrayList().orEmpty()
    }

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeByte(if (successful) 1 else 0)
        parcel.writeStringList(paths)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<DirectoryListingParcelable> {
        override fun createFromParcel(parcel: Parcel) = DirectoryListingParcelable(parcel)

        override fun newArray(size: Int): Array<DirectoryListingParcelable?> = arrayOfNulls(size)
    }
}
