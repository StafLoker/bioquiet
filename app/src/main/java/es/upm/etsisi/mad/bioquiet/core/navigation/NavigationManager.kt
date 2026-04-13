package es.upm.etsisi.mad.bioquiet.core.navigation

import android.content.Context
import android.content.Intent
import com.google.android.material.bottomnavigation.BottomNavigationView
import es.upm.etsisi.mad.bioquiet.R
import es.upm.etsisi.mad.bioquiet.ui.account.AccountActivity
import es.upm.etsisi.mad.bioquiet.ui.map.MainActivity

class NavigationManager(
    private val context: Context,
    bottomNavigationView: BottomNavigationView,
    selectedItemId: Int
) {

    init {
        bottomNavigationView.selectedItemId = selectedItemId

        bottomNavigationView.setOnItemSelectedListener { item ->
            val intent = when (item.itemId) {
                R.id.nav_map -> Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                R.id.nav_stats -> Intent(context, AccountActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                else -> return@setOnItemSelectedListener false
            }

            context.startActivity(intent)
            true
        }

        // Ignore taps on the already-selected tab
        bottomNavigationView.setOnItemReselectedListener { }
    }
}
