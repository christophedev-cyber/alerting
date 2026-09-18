package com.sophiaengineering.alerting

/** État d'alimentation du téléphone. */
enum class PowerState { ON_AC, ON_BATTERY, UNKNOWN }

/** Action décidée par la machine à états à la suite d'un événement d'alimentation. */
sealed class AlertAction {
    /** Le téléphone vient de passer sur batterie : envoi immédiat + renvois périodiques. */
    object StartBatteryAlerts : AlertAction()

    /** Le secteur vient d'être rétabli : arrêt des renvois + email de rétablissement. */
    object StopAndNotifyRestored : AlertAction()

    /** Aucune transition pertinente. */
    object None : AlertAction()
}

/**
 * Logique pure décidant l'action à prendre lors d'une transition d'alimentation.
 * Ne dépend d'aucune API Android : entièrement testable en JVM pure.
 */
class AlertStateMachine(initial: PowerState = PowerState.UNKNOWN) {

    var state: PowerState = initial
        private set

    fun onPowerEvent(newState: PowerState): AlertAction {
        val previous = state
        state = newState
        return when {
            newState == PowerState.ON_BATTERY && previous != PowerState.ON_BATTERY ->
                AlertAction.StartBatteryAlerts

            newState == PowerState.ON_AC && previous == PowerState.ON_BATTERY ->
                AlertAction.StopAndNotifyRestored

            else -> AlertAction.None
        }
    }
}
