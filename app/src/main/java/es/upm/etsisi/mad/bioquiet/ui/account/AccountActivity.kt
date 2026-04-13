package es.upm.etsisi.mad.bioquiet.ui.account

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.firebase.ui.auth.AuthUI
import com.firebase.ui.auth.FirebaseAuthUIActivityResultContract
import com.firebase.ui.auth.data.model.FirebaseAuthUIAuthenticationResult
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

    private val signInLauncher = registerForActivityResult(
        FirebaseAuthUIActivityResultContract()
    ) { result -> onSignInResult(result) }

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

        binding.loginButton.setOnClickListener { launchSignInFlow() }
        binding.logoutButton.setOnClickListener { showLogoutConfirmationDialog() }

        observeState()
    }

    private fun launchSignInFlow() {
        val providers = arrayListOf(
            AuthUI.IdpConfig.EmailBuilder().build(),
            AuthUI.IdpConfig.GoogleBuilder().build()
        )
        signInLauncher.launch(
            AuthUI.getInstance()
                .createSignInIntentBuilder()
                .setAvailableProviders(providers)
                .build()
        )
    }

    private fun onSignInResult(result: FirebaseAuthUIAuthenticationResult) {
        if (result.resultCode == RESULT_OK) {
            viewModel.onLoginSuccess()
        }
    }

    private fun showLogoutConfirmationDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle(R.string.account_logout)

        builder.setMessage(R.string.account_logout_confirm_message)

        builder.setPositiveButton(R.string.account_logout_confirm_positive) { _, _ ->
            viewModel.logout(this)
            Toast.makeText(this, R.string.account_logout_success, Toast.LENGTH_LONG).show()
        }
        builder.setNegativeButton(R.string.account_logout_confirm_negative) { dialog, _ ->
            dialog.cancel()
        }

        builder.show()
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    val loggedIn = state.userId != null
                    binding.userId.text = state.userId ?: "—"
                    binding.loginButton.visibility = if (loggedIn) View.GONE else View.VISIBLE
                    binding.logoutButton.visibility = if (loggedIn) View.VISIBLE else View.GONE
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
        binding.totalRecords.text =
            getString(R.string.account_total_records_value, stats.totalRecords)
        binding.maxDb.text = getString(R.string.account_max_db_value, stats.maxDb)
        binding.averageDb.text = getString(R.string.account_avg_db_value, stats.averageDb)
        binding.feedback.text = stats.feedback
    }
}
