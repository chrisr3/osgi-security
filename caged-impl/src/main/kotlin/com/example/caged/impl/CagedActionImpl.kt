package com.example.caged.impl

import com.example.caged.CagedAction
import org.osgi.framework.AdminPermission
import org.osgi.framework.AdminPermission.CONTEXT
import org.osgi.framework.Bundle
import org.osgi.framework.FrameworkUtil
import org.osgi.service.component.annotations.Component
import org.osgi.service.condpermadmin.BundleLocationCondition
import org.osgi.service.condpermadmin.ConditionInfo
import org.slf4j.LoggerFactory

@Suppress("unused")
@Component
class CagedActionImpl : CagedAction {
    private val logger = LoggerFactory.getLogger(CagedActionImpl::class.java)

    private fun checkBundleLocationCondition(bundle: Bundle) {
        val conditionInfo = ConditionInfo(BundleLocationCondition::class.java.name, arrayOf("CAGE/*"))
        require(BundleLocationCondition.getCondition(bundle, conditionInfo).isSatisfied) {
            "BundleLocationCondition $conditionInfo is not satisfied"
        }
    }

    override fun execute() {
        logger.info("Executing action")

        val bundle = FrameworkUtil.getBundle(this::class.java)
        logger.info("- Bundle location: ${bundle.location}")
        checkBundleLocationCondition(bundle)

        System.getSecurityManager()?.also { sm ->
            // I expect this AdminPermission to be denied, but it isn't.
            val admin = AdminPermission("(location=${bundle.location})", CONTEXT)

            // However, this particular AdminPermission is denied.
            //val admin = AdminPermission("*", CONTEXT)

            logger.info("- Checking $admin")
            sm.checkPermission(admin)
        } ?: throw IllegalStateException("No SecurityManager installed")

        // This should be denied too, but isn't.
        logger.info("- Requesting BundleContext")
        bundle.bundleContext

        logger.info("Completed successfully")
    }
}
