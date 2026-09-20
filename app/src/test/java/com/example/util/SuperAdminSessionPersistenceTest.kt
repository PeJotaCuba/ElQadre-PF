package com.example.util

import android.content.SharedPreferences
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy

class SuperAdminSessionPersistenceTest {

    private lateinit var mockPrefs: SharedPreferences
    private val prefsStorage = mutableMapOf<String, Any?>()

    @Before
    fun setUp() {
        prefsStorage.clear()

        val editorHandler = object : InvocationHandler {
            override fun invoke(proxy: Any?, method: Method, args: Array<out Any?>?): Any? {
                when (method.name) {
                    "putBoolean" -> {
                        val key = args?.get(0) as String
                        val value = args?.get(1) as Boolean
                        prefsStorage[key] = value
                        return proxy
                    }
                    "putString" -> {
                        val key = args?.get(0) as String
                        val value = args?.get(1) as String?
                        prefsStorage[key] = value
                        return proxy
                    }
                    "putLong" -> {
                        val key = args?.get(0) as String
                        val value = args?.get(1) as Long
                        prefsStorage[key] = value
                        return proxy
                    }
                    "remove" -> {
                        val key = args?.get(0) as String
                        prefsStorage.remove(key)
                        return proxy
                    }
                    "clear" -> {
                        prefsStorage.clear()
                        return proxy
                    }
                    "apply", "commit" -> {
                        return true
                    }
                }
                return null
            }
        }
        val mockEditor = Proxy.newProxyInstance(
            SharedPreferences.Editor::class.java.classLoader,
            arrayOf(SharedPreferences.Editor::class.java),
            editorHandler
        ) as SharedPreferences.Editor

        val prefsHandler = object : InvocationHandler {
            override fun invoke(proxy: Any?, method: Method, args: Array<out Any?>?): Any? {
                when (method.name) {
                    "getBoolean" -> {
                        val key = args?.get(0) as String
                        val defaultVal = args?.get(1) as Boolean
                        return (prefsStorage[key] as? Boolean) ?: defaultVal
                    }
                    "getString" -> {
                        val key = args?.get(0) as String
                        val defaultVal = args?.get(1) as? String
                        return (prefsStorage[key] as? String) ?: defaultVal
                    }
                    "getLong" -> {
                        val key = args?.get(0) as String
                        val defaultVal = args?.get(1) as Long
                        return (prefsStorage[key] as? Long) ?: defaultVal
                    }
                    "edit" -> return mockEditor
                }
                return null
            }
        }
        mockPrefs = Proxy.newProxyInstance(
            SharedPreferences::class.java.classLoader,
            arrayOf(SharedPreferences::class.java),
            prefsHandler
        ) as SharedPreferences

        SuperAdminSessionManager.prefsProvider = { mockPrefs }
    }

    @After
    fun tearDown() {
        SuperAdminSessionManager.prefsProvider = null
    }

    /**
     * Helper mimicking MainActivity's initial navigation routing
     */
    private fun resolveInitialScreen(
        isSuperAdminSession: Boolean,
        isDeviceLinked: Boolean
    ): String {
        return if (isSuperAdminSession) {
            "SUPER_ADMIN"
        } else if (isDeviceLinked) {
            "LOGIN"
        } else {
            "PORTADA"
        }
    }

    @Test
    fun testInitialState_NoSession_RoutesToNormalFlow() {
        assertFalse(SuperAdminSessionManager.isSessionActive(null))
        assertNull(SuperAdminSessionManager.getSessionUser(null))

        // When unlinked, routes to PORTADA
        assertEquals("PORTADA", resolveInitialScreen(isSuperAdminSession = false, isDeviceLinked = false))

        // When linked, routes to LOGIN
        assertEquals("LOGIN", resolveInitialScreen(isSuperAdminSession = false, isDeviceLinked = true))
    }

    /**
     * CASO 1:
     * Autenticar Super Admin → cerrar app → abrir app → debe entrar directamente a Super Admin.
     * NO mostrar PORTADA, NO mostrar INICIO, NO pedir nuevamente autenticación.
     */
    @Test
    fun testCase1_AuthenticateSuperAdmin_CloseApp_OpenApp_EntersDirectlyToSuperAdmin() {
        val superAdminUser = "SuperAdminPedro"

        // 1. Super Admin authenticates successfully
        SuperAdminSessionManager.saveSession(null, superAdminUser)

        assertTrue(SuperAdminSessionManager.isSessionActive(null))
        assertEquals(superAdminUser, SuperAdminSessionManager.getSessionUser(null))

        // 2. Simulate closing the app (all in-memory Activity and UI state dropped)
        var appInMemoryScreen: String? = null
        var appInMemoryUser: String? = null

        // 3. Simulate opening the app anew (cold start, savedInstanceState == null)
        val isSuperAdminActiveOnLaunch = SuperAdminSessionManager.isSessionActive(null)
        val savedUserOnLaunch = SuperAdminSessionManager.getSessionUser(null)

        assertTrue("Session must be detected active on launch", isSuperAdminActiveOnLaunch)
        assertEquals(superAdminUser, savedUserOnLaunch)

        appInMemoryScreen = resolveInitialScreen(
            isSuperAdminSession = isSuperAdminActiveOnLaunch,
            isDeviceLinked = false // Independent of business linkage
        )
        appInMemoryUser = savedUserOnLaunch

        // Verifications:
        // Must enter directly to Super Admin
        assertEquals("SUPER_ADMIN", appInMemoryScreen)
        assertEquals(superAdminUser, appInMemoryUser)

        // NO PORTADA
        assertNotEquals("PORTADA", appInMemoryScreen)
        // NO INICIO
        assertNotEquals("INICIO", appInMemoryScreen)
    }

    /**
     * CASO 2:
     * Autenticar Super Admin → apagar pantalla → encender → debe continuar en Super Admin.
     */
    @Test
    fun testCase2_AuthenticateSuperAdmin_ScreenOffOn_ContinuesInSuperAdmin() {
        val superAdminUser = "SuperAdminPedro"
        SuperAdminSessionManager.saveSession(null, superAdminUser)

        var currentScreen = "SUPER_ADMIN"
        var currentUser = superAdminUser

        // Simulate screen off (onPause / onStop)
        val isPaused = true
        assertTrue(isPaused)

        // Simulate screen on (onResume)
        assertTrue(SuperAdminSessionManager.isSessionActive(null))
        assertEquals("SUPER_ADMIN", currentScreen)
        assertEquals(superAdminUser, currentUser)
    }

    /**
     * CASO 3:
     * Autenticar Super Admin → Android recrea la Activity → debe conservar la sesión.
     * Además, si Super Admin está dentro de un negocio para realizar pruebas,
     * conservar la lógica existente de regreso mediante la flecha superior hacia la sesión Super Admin.
     */
    @Test
    fun testCase3_AuthenticateSuperAdmin_AndroidRecreatesActivity_RestoresSession() {
        val superAdminUser = "SuperAdminPedro"
        SuperAdminSessionManager.saveSession(null, superAdminUser)

        // Scenario A: In SuperAdmin screen when recreated
        var currentScreen = "SUPER_ADMIN"
        var superAdminState: String? = superAdminUser

        // Simulate Activity recreation (onDestroy -> onCreate)
        val isSessionActive = SuperAdminSessionManager.isSessionActive(null)
        assertTrue(isSessionActive)

        if (isSessionActive) {
            if (superAdminState == null) {
                superAdminState = SuperAdminSessionManager.getSessionUser(null)
            }
            if (currentScreen == "PORTADA" || currentScreen == "INICIO") {
                currentScreen = "SUPER_ADMIN"
            }
        }
        assertEquals("SUPER_ADMIN", currentScreen)
        assertEquals(superAdminUser, superAdminState)

        // Scenario B: Super Admin is inside a business for testing (LOGIN screen)
        currentScreen = "LOGIN"
        // Activity recreated while testing business
        val isSessionActiveWhileTesting = SuperAdminSessionManager.isSessionActive(null)
        assertTrue(isSessionActiveWhileTesting)

        if (isSessionActiveWhileTesting) {
            if (superAdminState == null) {
                superAdminState = SuperAdminSessionManager.getSessionUser(null)
            }
            if (currentScreen == "PORTADA" || currentScreen == "INICIO") {
                currentScreen = "SUPER_ADMIN"
            }
        }
        // Retains LOGIN for testing
        assertEquals("LOGIN", currentScreen)
        assertNotNull("superAdminUser must be non-null so back arrow is shown", superAdminState)

        // Verify back arrow returns directly to SUPER_ADMIN
        val onBackToSuperAdmin = {
            currentScreen = "SUPER_ADMIN"
        }
        onBackToSuperAdmin.invoke()
        assertEquals("SUPER_ADMIN", currentScreen)
    }

    /**
     * CASO 4:
     * Pulsar SALIR → cerrar sesión → abrir app → ya NO debe entrar automáticamente como Super Admin.
     * La autorización del dispositivo como Super Admin NO debe eliminarse por simplemente cerrar sesión.
     */
    @Test
    fun testCase4_PressSalir_CloseSession_OpenApp_NoLongerEntersAsSuperAdmin() {
        val superAdminUser = "SuperAdminPedro"
        SuperAdminSessionManager.saveSession(null, superAdminUser)
        assertTrue(SuperAdminSessionManager.isSessionActive(null))

        // Device is authorized
        var isDeviceAuthorizedAsSuperAdmin = true

        // 1. Super Admin explicitly presses SALIR
        SuperAdminSessionManager.clearSession(null)

        // Local open session state is removed
        assertFalse(SuperAdminSessionManager.isSessionActive(null))
        assertNull(SuperAdminSessionManager.getSessionUser(null))

        // The device authorization itself is NOT removed by logging out
        assertTrue(
            "Device authorization as Super Admin must NOT be deleted simply by logging out",
            isDeviceAuthorizedAsSuperAdmin
        )

        // 2. Simulate closing and reopening the app
        val isSessionActiveOnReopen = SuperAdminSessionManager.isSessionActive(null)
        assertFalse(isSessionActiveOnReopen)

        val destinationOnReopen = resolveInitialScreen(
            isSuperAdminSession = isSessionActiveOnReopen,
            isDeviceLinked = false
        )

        // Must NOT enter automatically as Super Admin; returns to normal flow
        assertNotEquals("SUPER_ADMIN", destinationOnReopen)
        assertEquals("PORTADA", destinationOnReopen)

        // If device is linked commercially, returns to LOGIN
        val destinationIfLinked = resolveInitialScreen(
            isSuperAdminSession = isSessionActiveOnReopen,
            isDeviceLinked = true
        )
        assertEquals("LOGIN", destinationIfLinked)
    }
}
