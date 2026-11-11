package io.github.sds100.keymapper.data.entities

import android.os.Parcelable
import com.google.gson.annotations.SerializedName
import java.util.UUID
import kotlinx.parcelize.Parcelize

@Parcelize
data class MotionTriggerKeyEntity(
    @SerializedName(NAME_DIRECTIONS)
    val directions: List<String> = emptyList(),

    @SerializedName(NAME_CLICK_TYPE)
    override val clickType: Int = SHORT_PRESS,

    @SerializedName(NAME_UID)
    override val uid: String = UUID.randomUUID().toString(),
) : TriggerKeyEntity(),
    Parcelable {

    companion object {
        const val NAME_DIRECTIONS = "directions"
    }
}
