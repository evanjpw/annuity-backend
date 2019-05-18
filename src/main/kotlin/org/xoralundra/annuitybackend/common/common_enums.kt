package org.xoralundra.annuitybackend.common


enum class PolicyState(val decription: String) {
    NEW("Awaiting Premium Deposit"),
    CANCELLABLE("Active/Cancellable"),
    ACTIVE("Active"),
    CANCELLED("Close/Cancelled"),
    COMPLETED("Closed/Term Completed"),
    RIP("Closed/Owner Died");

    val INITIAL_STATES get() = listOf(NEW)
    val ACTIVE_STATES get () = listOf(CANCELLABLE, ACTIVE)
    val CLOSED_STATES get() = listOf(CANCELLED, COMPLETED, RIP)

    fun isTransitionValid(newState: PolicyState) : Boolean {
        return when (this) {
            in INITIAL_STATES -> newState in listOf(CANCELLABLE, CANCELLED)
            CANCELLABLE -> newState in listOf(ACTIVE) + CLOSED_STATES
            ACTIVE -> newState in CLOSED_STATES
            else -> false
        }
    }
}

enum class ApplicationState {
    OK,
    WAIT,
    DISAPPROVE
}


enum class PayoutType {
    PAYMENT,
    REFUND,
    TERM_COMPLETE,
    TO_BENEFICIARIES
}