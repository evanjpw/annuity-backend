package org.xoralundra.annuitybackend.core

import java.lang.IllegalArgumentException
import java.time.ZonedDateTime
import java.util.*
import kotlin.collections.ArrayList

class WrongInsuredException(s: String?) : IllegalArgumentException(s) {
    constructor() : this(null)
}

data class Address(
    val street1: String,
    val street2: String?,
    val city: String,
    val state: String?,
    val postalCode: String?,
    val countryCode: String
)

class Insured(val name: String, address: Address, val phoneNumber: String, val dateOfBirth: Date, taxpayerIdentificationNumber: String) {
    private var _dateOfDeath: ZonedDateTime? = null
    val isAlive get() = _dateOfDeath == null
    val dateOfDeath get() = _dateOfDeath

    fun died(deathDate: ZonedDateTime? = null) {
        _dateOfDeath = deathDate ?: ZonedDateTime.now()
    }

    private val _policies = ArrayList<Policy>()

    val policies: List<Policy> get() = _policies.toList()

    fun addPolicy(policy: Policy) {
        if (policy.insured != this) {
            throw WrongInsuredException()
        }
        _policies.add(policy)
    }
}
