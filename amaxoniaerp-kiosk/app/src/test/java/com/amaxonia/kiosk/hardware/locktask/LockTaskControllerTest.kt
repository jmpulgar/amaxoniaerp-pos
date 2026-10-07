package com.amaxonia.kiosk.hardware.locktask

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LockTaskControllerTest {
    @Test
    fun isDeviceOwner_delegatesToDevicePolicyManager() {
        val context = mockk<Context>(relaxed = true)
        val dpm = mockk<DevicePolicyManager>(relaxed = true)
        val adminComponent = mockk<ComponentName>()

        every { context.packageName } returns "com.amaxonia.kiosk"
        every { dpm.isDeviceOwnerApp("com.amaxonia.kiosk") } returns true

        val controller = LockTaskController(context, dpm, adminComponent)
        assertTrue(controller.isDeviceOwner())

        every { dpm.isDeviceOwnerApp("com.amaxonia.kiosk") } returns false
        assertFalse(controller.isDeviceOwner())
    }

    @Test
    fun isLockTaskPermitted_delegatesToDevicePolicyManager() {
        val context = mockk<Context>(relaxed = true)
        val dpm = mockk<DevicePolicyManager>(relaxed = true)
        val adminComponent = mockk<ComponentName>()

        every { context.packageName } returns "com.amaxonia.kiosk"
        every { dpm.isLockTaskPermitted("com.amaxonia.kiosk") } returns true

        val controller = LockTaskController(context, dpm, adminComponent)
        assertTrue(controller.isLockTaskPermitted())
    }

    @Test
    fun startLockTask_invokesActivityStartLockTaskAndUpdatesState() {
        val context = mockk<Context>(relaxed = true)
        val dpm = mockk<DevicePolicyManager>(relaxed = true)
        val adminComponent = mockk<ComponentName>()
        val activity = mockk<Activity>(relaxed = true)

        every { context.packageName } returns "com.amaxonia.kiosk"
        every { dpm.isDeviceOwnerApp("com.amaxonia.kiosk") } returns false

        val controller = LockTaskController(context, dpm, adminComponent)
        assertFalse(controller.isLockTaskActive.value)

        controller.startLockTask(activity)

        verify(exactly = 1) { activity.startLockTask() }
        assertTrue(controller.isLockTaskActive.value)
    }

    @Test
    fun stopLockTask_invokesActivityStopLockTaskAndUpdatesState() {
        val context = mockk<Context>(relaxed = true)
        val dpm = mockk<DevicePolicyManager>(relaxed = true)
        val adminComponent = mockk<ComponentName>()
        val activity = mockk<Activity>(relaxed = true)

        val controller = LockTaskController(context, dpm, adminComponent)
        controller.startLockTask(activity)
        assertTrue(controller.isLockTaskActive.value)

        controller.stopLockTask(activity)

        verify(exactly = 1) { activity.stopLockTask() }
        assertFalse(controller.isLockTaskActive.value)
    }

    @Test
    fun configureKioskPolicies_whenDeviceOwner_setsPolicies() {
        val context = mockk<Context>(relaxed = true)
        val dpm = mockk<DevicePolicyManager>(relaxed = true)
        val adminComponent = mockk<ComponentName>()

        every { context.packageName } returns "com.amaxonia.kiosk"
        every { dpm.isDeviceOwnerApp("com.amaxonia.kiosk") } returns true

        val controller = LockTaskController(context, dpm, adminComponent)
        controller.configureKioskPolicies()

        verify {
            dpm.setLockTaskPackages(any(), any())
            dpm.setLockTaskFeatures(any(), DevicePolicyManager.LOCK_TASK_FEATURE_NONE)
            dpm.setKeyguardDisabled(any(), true)
            dpm.setStatusBarDisabled(any(), true)
        }
    }
}
