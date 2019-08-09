package ai.chench.assidua

import ai.chench.assidua.data.Budget
import ai.chench.assidua.data.Expenditure
import ai.chench.assidua.data.ExpenditureViewModel
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.synthetic.main.fragment_display_budget.*
import kotlinx.android.synthetic.main.fragment_display_budget.view.*
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.*

class DisplayBudgetFragment : Fragment() {

    companion object {
        // Used for passing in the budget UUID via arguments to this fragment
        const val ARGUMENT_BUDGET_UUID = "ARGUMENT_BUDGET_UUID"
        private const val TAG = "DisplayBudgetFragment"
    }

    private lateinit var viewModel: ExpenditureViewModel
    private lateinit var adapter: ExpenditureAdapter
    private var budget: Budget? = null
    private lateinit var budgetId: UUID  // Id of the budget this fragment is displaying

    /**
     * @return Whether this fragment is currently displaying an actual budget or not.
     */
    fun hasValidBudget(): Boolean =
        viewModel.getBudget(budgetId) == null


    private val clickListener = View.OnClickListener { view ->
        when (view) {
            addExpenditureButton -> {
                // We haven't found the budget yet from the database.
                // This means that everything's going really slowly or
                // more likely, something's wrong.
                if (budget == null) { return@OnClickListener }

                var expenditureValue: BigDecimal
                try {
                    // Round input to two decimal places
                    expenditureValue = BigDecimal(expenditureCostEditText.text.toString()).setScale(2, RoundingMode.HALF_DOWN)
                } catch (e: NumberFormatException) {
                    // check that the input value is valid
                    expenditureCostEditText.setError(getString(R.string.error_not_a_number))
                    return@OnClickListener
                }

                // Check to see whether entered expenditure was an expense or income.
                if (incomeSwitch.isChecked) {
                    // If switch is checked, it's income. No modifications needed
                } else {
                    // Otherwise, it's an expense, so multiply by -1.
                    expenditureValue = expenditureValue.negate()
                }

                var name = expenditureNameEditText.text.toString().trim()
                name = if (name == "") getString(R.string.untitled_expenditure) else name

                budget?.let {
                    viewModel.addExpenditure(
                            Expenditure(name, expenditureValue, Date(), budgetId),
                            it)
                }
                expenditureNameEditText.setText("")
                expenditureCostEditText.setText("")
            }

            undoExpenditureButton -> {
                budget?.let { viewModel.deleteLastExpenditure(it) }
            }
        }
    }

    fun setBudgetId(budgetId: UUID) {
        this.budgetId = budgetId
        if (::viewModel.isInitialized) {
            updateViews()
        }
    }

    private fun updateViews() {
        // Budget may be null if budget was just deleted. No big deal
        budget = viewModel.getBudget(budgetId)
        if (budget == null) { return }

        Log.d(TAG, "Budget '${budget?.name}' just had its observer called! Balance: ${budget?.balance}")

        adapter.setExpenditures(budget?.expenditures)

        // Scroll to the top to see the most recently added transaction
        expendituresRecyclerView.smoothScrollToPosition(
                if (adapter.itemCount - 1 < 0) 0
                else adapter.itemCount - 1
        )

        val balance = budget?.balance
        activity?.runOnUiThread {
            remainingMoneyTextView.setText(balance?.setScale(2)?.toPlainString()) // Set the number to always have 2 decimal places

            if (balance != null && balance < BigDecimal(0)) {
                // user has negative balance, set TextView to a certain color.
                context?.resources?.let {remainingMoneyTextView.setTextColor(it.getColor(R.color.colorNegative) )}
            } else {
                // user is positive in balance! Set TextView to a certain color.
                context?.resources?.let {remainingMoneyTextView.setTextColor(it.getColor(R.color.colorPositive) )}
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {

        val view = inflater.inflate(R.layout.fragment_display_budget, container, false)

        if (null != activity) {
            // Attempt to share the ViewModel between the activity and all its fragments.
            viewModel = ViewModelProviders.of(activity!!).get(ExpenditureViewModel::class.java)
        } else {
            viewModel = ViewModelProviders.of(this).get(ExpenditureViewModel::class.java)
        }

        budgetId = UUID.fromString(arguments!!.getString(ARGUMENT_BUDGET_UUID))
        val budgets = viewModel.budgets

        budgets.observe(this, Observer {
            // TODO figure out how to only observe one budget instead of literally every single one
            updateViews()
        })

        view.addExpenditureButton.setOnClickListener(clickListener)
        view.undoExpenditureButton.setOnClickListener(clickListener)

        adapter = ExpenditureAdapter()

        val layoutManager = LinearLayoutManager(context, RecyclerView.VERTICAL, true)
        layoutManager.stackFromEnd = true

        view.expendituresRecyclerView.adapter = adapter
        view.expendituresRecyclerView.layoutManager = layoutManager

        view.incomeSwitch.setOnCheckedChangeListener { _, checked ->
            view.switchTextView.text = if (checked) getString(R.string.income) else getString(R.string.expense)
        }

        view.settingsButton.setOnClickListener {
            // Launch the settings fragment to allow the user to modify this budget
            val settingsFragment = BudgetSettingsFragment()

            // Put UUID as argument to the fragment
            val args = Bundle().apply {
                putString(BudgetSettingsFragment.ARGUMENT_BUDGET_UUID, budgetId.toString())
            }

            settingsFragment.arguments = args

            fragmentManager?.beginTransaction()?.replace(
                    R.id.settings,
                    settingsFragment
            )?.commitAllowingStateLoss()
        }

        // When the user finishes with the name entry field, automatically insert the expenditure.
        // Then, switch the focus back to the expenditure value edit text
        view.expenditureNameEditText.setOnEditorActionListener { textView, actionId, keyEvent ->
            return@setOnEditorActionListener when(actionId) {
                EditorInfo.IME_ACTION_GO -> {
                    view.addExpenditureButton.performClick()
                    view.expenditureCostEditText.requestFocus()
                    true
                }
                else -> false
            }
        }

        return view
    }
}