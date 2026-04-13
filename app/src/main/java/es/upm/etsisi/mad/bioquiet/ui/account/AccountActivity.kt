package es.upm.etsisi.mad.bioquiet.ui.account

import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import es.upm.etsisi.mad.bioquiet.R
import es.upm.etsisi.mad.bioquiet.core.navigation.NavigationManager
import es.upm.etsisi.mad.bioquiet.data.repository.NoiseRepository
import es.upm.etsisi.mad.bioquiet.databinding.ActivityAccountBinding
import es.upm.etsisi.mad.bioquiet.model.Statistics
import kotlinx.coroutines.launch

class AccountActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAccountBinding

    private val viewModel: AccountViewModel by viewModels {
        AccountViewModel.Factory(NoiseRepository(this))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityAccountBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        NavigationManager(
            context = this,
            bottomNavigationView = binding.bottomNavigation,
            selectedItemId = R.id.nav_stats
        )

        observeState()
    }

    override fun onBackPressed() {
        // Intentionally disabled — navigation is via the bottom bar only
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.loadingIndicator.visibility =
                        if (state.isLoading) View.VISIBLE else View.GONE
                    binding.emptyState.visibility = if (state.isEmpty) View.VISIBLE else View.GONE
                    binding.statsContent.visibility =
                        if (state.statistics != null) View.VISIBLE else View.GONE
                    state.statistics?.let { renderStatistics(it) }
                }
            }
        }
    }

    private fun renderStatistics(stats: Statistics) {
        binding.totalRecords.text = stats.totalRecords.toString()
        binding.maxDb.text = "${stats.maxDb} dB"
        binding.averageDb.text = "${stats.averageDb} dB"
        binding.feedback.text = stats.feedback
    }
}
