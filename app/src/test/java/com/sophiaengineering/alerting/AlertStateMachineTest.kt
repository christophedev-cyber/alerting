package com.sophiaengineering.alerting

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class AlertStateMachineTest {

    @Test
    fun `passage sur batterie depuis secteur declenche les alertes`() {
        val sm = AlertStateMachine(PowerState.ON_AC)
        assertSame(AlertAction.StartBatteryAlerts, sm.onPowerEvent(PowerState.ON_BATTERY))
        assertEquals(PowerState.ON_BATTERY, sm.state)
    }

    @Test
    fun `passage sur batterie depuis etat inconnu declenche les alertes`() {
        val sm = AlertStateMachine(PowerState.UNKNOWN)
        assertSame(AlertAction.StartBatteryAlerts, sm.onPowerEvent(PowerState.ON_BATTERY))
    }

    @Test
    fun `retour secteur depuis batterie notifie le retablissement`() {
        val sm = AlertStateMachine(PowerState.ON_BATTERY)
        assertSame(AlertAction.StopAndNotifyRestored, sm.onPowerEvent(PowerState.ON_AC))
        assertEquals(PowerState.ON_AC, sm.state)
    }

    @Test
    fun `demarrage sur secteur ne declenche aucune action`() {
        val sm = AlertStateMachine(PowerState.UNKNOWN)
        assertSame(AlertAction.None, sm.onPowerEvent(PowerState.ON_AC))
    }

    @Test
    fun `rester sur batterie ne redeclenche pas les alertes`() {
        val sm = AlertStateMachine(PowerState.ON_BATTERY)
        assertSame(AlertAction.None, sm.onPowerEvent(PowerState.ON_BATTERY))
    }

    @Test
    fun `rester sur secteur ne fait rien`() {
        val sm = AlertStateMachine(PowerState.ON_AC)
        assertSame(AlertAction.None, sm.onPowerEvent(PowerState.ON_AC))
    }

    @Test
    fun `cycle complet batterie puis secteur`() {
        val sm = AlertStateMachine(PowerState.ON_AC)
        assertSame(AlertAction.StartBatteryAlerts, sm.onPowerEvent(PowerState.ON_BATTERY))
        assertSame(AlertAction.StopAndNotifyRestored, sm.onPowerEvent(PowerState.ON_AC))
        assertSame(AlertAction.None, sm.onPowerEvent(PowerState.ON_AC))
    }
}
