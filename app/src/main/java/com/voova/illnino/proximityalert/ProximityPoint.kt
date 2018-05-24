package com.voova.illnino.proximityalert

import android.os.Parcel
import android.os.Parcelable

data class ProximityPoint(
        var id: Int,
        var lat: Double,
        var lon: Double,
        var radius: Float,
        var name: String
) : Parcelable {
    constructor(source: Parcel) : this(
            source.readInt(),
            source.readDouble(),
            source.readDouble(),
            source.readFloat(),
            source.readString()
    )

    override fun describeContents() = 0

    override fun writeToParcel(dest: Parcel, flags: Int) {
        with(dest) {
            writeInt(id)
            writeDouble(lat)
            writeDouble(lon)
            writeFloat(radius)
            writeString(name)
        }
    }

    companion object {
        @JvmField
        val CREATOR: Parcelable.Creator<ProximityPoint> = object : Parcelable.Creator<ProximityPoint> {
            override fun createFromParcel(source: Parcel): ProximityPoint = ProximityPoint(source)
            override fun newArray(size: Int): Array<ProximityPoint?> = arrayOfNulls(size)
        }
    }
}