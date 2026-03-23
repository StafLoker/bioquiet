package es.upm.etsisi.mad.bioquiet.util

import android.view.View
import com.google.android.material.bottomnavigation.BottomNavigationView
import es.upm.etsisi.mad.bioquiet.R

class NavigationManager(
    private val mapContainer: View,
    private val statsContainer: View,
    bottomNavigationView: BottomNavigationView,
    private val onStatsSelected: () -> Unit
) {
    init {
        // Configuration when buttons are touched
        bottomNavigationView.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_map -> {
                    showMap()
                    true
                }
                R.id.nav_stats -> {
                    onStatsSelected()
                    showStats()
                    true
                }
                else -> false
            }
        }
    }

    private fun showMap() {
        mapContainer.visibility = View.VISIBLE
        statsContainer.visibility = View.GONE
    }

    private fun showStats() {
        mapContainer.visibility = View.GONE
        statsContainer.visibility = View.VISIBLE
    }
}
