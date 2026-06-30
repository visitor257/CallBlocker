package com.callblocker.app.util

import android.content.Context
import android.telephony.SubscriptionManager

data class SimInfo(
    val simSlot: Int,
    val displayName: String,
    val subscriptionId: Int
)

object SimUtils {
    fun getActiveSims(context: Context): List<SimInfo> {
        val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
            ?: return listOf(SimInfo(0, context.getString(com.callblocker.app.R.string.sim_card_default_1), -1))

        return subscriptionManager.activeSubscriptionInfoList?.mapNotNull { info ->
            try {
                val slotIndex = info.simSlotIndex
                val name = info.displayName?.toString() ?: context.getString(com.callblocker.app.R.string.sim_card_format, slotIndex + 1)
                SimInfo(slotIndex, name, info.subscriptionId)
            } catch (_: Exception) { null }
        }?.sortedBy { it.simSlot }
            ?: listOf(
                SimInfo(0, context.getString(com.callblocker.app.R.string.sim_card_default_1), -1),
                SimInfo(1, context.getString(com.callblocker.app.R.string.sim_card_default_2), -1)
            )
    }

    fun getDefaultSims(context: Context): List<SimInfo> {
        return listOf(
            SimInfo(0, context.getString(com.callblocker.app.R.string.sim_card_default_1), -1),
            SimInfo(1, context.getString(com.callblocker.app.R.string.sim_card_default_2), -1)
        )
    }

}
