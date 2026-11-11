package io.github.sds100.keymapper.base.trigger

import io.github.sds100.keymapper.base.keymaps.ClickType
import io.github.sds100.keymapper.data.entities.MotionTriggerKeyEntity
import io.github.sds100.keymapper.data.entities.TriggerKeyEntity
import io.github.sds100.keymapper.system.motion.MotionDirection
import kotlinx.serialization.Serializable

@Serializable
data class MotionTriggerKey(
    val directions: List<MotionDirection>,
    override val clickType: ClickType = ClickType.SHORT_PRESS,
    override val uid: String = directions.joinToString("-") { it.name },
) : TriggerKey() {
    override val allowedLongPress: Boolean = false
    override val allowedDoublePress: Boolean = false

    override fun compareTo(other: TriggerKey) = when (other) {
        is MotionTriggerKey -> compareValuesBy(
            this,
            other,
            { it.directions.joinToString("-") { d -> d.name } },
            { it.clickType },
        )

        else -> super.compareTo(other)
    }

    companion object {
        fun fromEntity(entity: MotionTriggerKeyEntity): MotionTriggerKey {
            val directions = entity.directions.mapNotNull { name ->
                try {
                    MotionDirection.valueOf(name)
                } catch (_: IllegalArgumentException) {
                    null
                }
            }

            val clickType: ClickType = when (entity.clickType) {
                TriggerKeyEntity.SHORT_PRESS -> ClickType.SHORT_PRESS
                TriggerKeyEntity.LONG_PRESS -> ClickType.LONG_PRESS
                TriggerKeyEntity.DOUBLE_PRESS -> ClickType.DOUBLE_PRESS
                else -> ClickType.SHORT_PRESS
            }

            return MotionTriggerKey(
                directions = directions,
                clickType = clickType,
                uid = entity.uid,
            )
        }

        fun toEntity(key: MotionTriggerKey): MotionTriggerKeyEntity {
            val clickType: Int = when (key.clickType) {
                ClickType.SHORT_PRESS -> TriggerKeyEntity.SHORT_PRESS
                ClickType.LONG_PRESS -> TriggerKeyEntity.LONG_PRESS
                ClickType.DOUBLE_PRESS -> TriggerKeyEntity.DOUBLE_PRESS
            }

            return MotionTriggerKeyEntity(
                directions = key.directions.map { it.name },
                clickType = clickType,
                uid = key.uid,
            )
        }
    }
}
