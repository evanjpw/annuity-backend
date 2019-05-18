package org.xoralundra.annuitybackend.core

import org.xoralundra.annuitybackend.mockapis.PolicyNumber
import org.xoralundra.annuitybackend.mockapis.PolicyStore
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

typealias TIN = String

data class Death(val dateDeceased: java.time.ZonedDateTime, val insuredTIN: TIN)

class Insured(
    val name: String,
    val address: Address,
    val phoneNumber: String,
    val dateOfBirth: Date,
    val taxpayerIdentificationNumber: String,
    val policyNumbers: Array<PolicyNumber>
) {
    private var _dateOfDeath: ZonedDateTime? = null
    val isAlive get() = _dateOfDeath == null
    val dateOfDeath get() = _dateOfDeath

    fun died(deathDate: ZonedDateTime? = null) {
        _dateOfDeath = deathDate ?: ZonedDateTime.now()
        for (policy in _policies) {
            policy.ownerDied(_dateOfDeath!!)
        }
    }

    fun initPolicies(policyStore: PolicyStore) {
        for (policyNumber in policyNumbers) {
            val policy = policyStore.getPolicy(policyNumber)
            if (policy != null) {
                addPolicy(policy)
            }
        }
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
