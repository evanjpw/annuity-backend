package org.xoralundra.annuitybackend.mockapis

import java.util.*
import kotlin.collections.ArrayList

data class MsgRecord<K, V> (val key: K, val value: V)

interface  MsgProducer<K, V> {
    fun send(key: K, value: V)
}

interface MsgConsumer<K, V> {
    fun poll(): Collection<MsgRecord<K, V>>
}

object MockMsgQueues {
    val incomingPolicyEvents = ArrayDeque<MsgRecord<String, String>>()
    val outgoingBankingEvents = ArrayDeque<MsgRecord<String, String>>()

    object PolicyEventConsumer : MsgConsumer<String, String> {
        override fun poll(): Collection<MsgRecord<String, String>> {
            val records = ArrayList<MsgRecord<String, String>>()
            while (!incomingPolicyEvents.isEmpty()) {
                records.add(incomingPolicyEvents.poll())
            }
            return records
        }
    }

    object BankEventProducer : MsgProducer<String, String> {
        override fun send(key: String, value: String) {
            outgoingBankingEvents.offer(MsgRecord(key, value))
        }
    }
}