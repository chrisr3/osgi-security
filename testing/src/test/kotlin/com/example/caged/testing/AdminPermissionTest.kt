package com.example.caged.testing

import com.example.caged.CagedAction
import java.io.FileNotFoundException
import java.io.InputStream
import java.security.AccessControlException
import java.security.AllPermission
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.fail
import org.osgi.framework.AdminPermission
import org.osgi.framework.AdminPermission.CONTEXT
import org.osgi.framework.Bundle
import org.osgi.framework.BundleContext
import org.osgi.framework.Constants.SYSTEM_BUNDLE_ID
import org.osgi.service.component.annotations.RequireServiceComponentRuntime
import org.osgi.service.condpermadmin.BundleLocationCondition
import org.osgi.service.condpermadmin.ConditionInfo
import org.osgi.service.condpermadmin.ConditionalPermissionAdmin
import org.osgi.service.condpermadmin.ConditionalPermissionInfo.ALLOW
import org.osgi.service.condpermadmin.ConditionalPermissionInfo.DENY
import org.osgi.service.permissionadmin.PermissionInfo
import org.osgi.test.common.annotation.InjectBundleContext
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.context.BundleContextExtension
import org.osgi.test.junit5.service.ServiceExtension
import org.slf4j.LoggerFactory

@ExtendWith(value = [ BundleContextExtension::class, ServiceExtension::class ])
@RequireServiceComponentRuntime
class AdminPermissionTest {
    private val logger = LoggerFactory.getLogger(AdminPermissionTest::class.java)

    @InjectService(timeout = 1000)
    lateinit var conditionalPermissionAdmin: ConditionalPermissionAdmin

    @InjectBundleContext
    lateinit var bundleContext: BundleContext

    private lateinit var cagedBundle: Bundle

    @Suppress("SameParameterValue")
    private fun getInputStream(resourceName: String): InputStream {
        return this::class.java.classLoader.getResource(resourceName)?.openStream()
            ?: throw FileNotFoundException("No such resource: '$resourceName'")
    }

    private fun setPermissionsPolicy() {
        val forbidCagedAdmin = conditionalPermissionAdmin.newConditionalPermissionInfo(
            "forbidCagedAdmin",
            arrayOf(ConditionInfo(BundleLocationCondition::class.java.name, arrayOf("CAGE/*"))),
            arrayOf(PermissionInfo(AdminPermission::class.java.name, "*", "*")),
            DENY
        )
        val grantAll = conditionalPermissionAdmin.newConditionalPermissionInfo(
            "grantAll",
            null,
            arrayOf(PermissionInfo(AllPermission::class.java.name, "*", "*")),
            ALLOW
        )

        val permissionsUpdate = conditionalPermissionAdmin.newConditionalPermissionUpdate()
        val conditionalPermissions = permissionsUpdate.conditionalPermissionInfos

        with(conditionalPermissions) {
            clear()
            add(forbidCagedAdmin)
            add(grantAll)
        }

        if (!permissionsUpdate.commit()) {
            throw IllegalStateException("Unable to commit updated permissions.")
        }
    }

    private fun checkAdminPermission() {
        System.getSecurityManager()?.also { sm ->
            sm.checkPermission(AdminPermission("*", CONTEXT))
        } ?: fail("No SecurityManager installed")
    }

    @BeforeEach
    fun setup() {
        val systemContext = bundleContext.getBundle(SYSTEM_BUNDLE_ID).bundleContext
        cagedBundle = getInputStream("META-INF/caged.jar").buffered().use { input ->
            systemContext.installBundle( "CAGE/caged.jar", input)
        }
        cagedBundle.start()
    }

    @AfterEach
    fun done() {
        cagedBundle.stop()
    }

    @Test
    fun testPermissions() {
        logger.warn("LOCATION: ${cagedBundle.location}")

        setPermissionsPolicy()

        checkAdminPermission()

        val cagedContext = cagedBundle.bundleContext
        val ref = cagedContext.getServiceReference(CagedAction::class.java.name) ?: fail("No CagedAction service")
        try {
            val cagedAction = cagedContext.getService(ref) as CagedAction
            assertThrows<AccessControlException>("Action should be denied!") {
                cagedAction.execute()
            }
        } finally {
            cagedContext.ungetService(ref)
        }
    }
}
