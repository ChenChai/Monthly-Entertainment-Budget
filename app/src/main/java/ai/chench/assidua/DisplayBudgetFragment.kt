package ai.chench.assidua

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.LiveData
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
        public const val ARGUMENT_BUDGET_UUID = "ARGUMENT_BUDGET_UUID"
    }

    private lateinit var viewModel: ExpenditureViewModel
    private lateinit var adapter: ExpenditureAdapter
    private lateinit var budget: LiveData<Budget> // Id of the budget this fragment is displaying
    private lateinit var budgetUUID: UUID
    private var expenditures: MutableList<Expenditure> = ArrayList<Expenditure>()// A most-recent list of expenditures

    private val clickListener = View.OnClickListener { view ->
        when (view) {
            addExpenditureButton -> {
                // We haven't found the budget yet from the database.
                // This means that everything's going really slowly or
                // more likely, something's wrong.
                if (budget.value == null) { return@OnClickListener }

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

                viewModel.addExpenditure(
                        Expenditure(name, expenditureValue, Date(), budgetUUID),
                        budget.value!!)
                expenditureNameEditText.setText("")
                expenditureCostEditText.setText("")
            }

            undoExpenditureButton -> {
                if (expenditures.isNotEmpty() && budget.value != null) {
                    viewModel.deleteExpenditure(expenditures.last(), budget.value!!)
                    expenditures.removeAt(expenditures.size - 1) // remove last element from local values
                }
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


        budgetUUID = UUID.fromString(arguments!!.getString(ARGUMENT_BUDGET_UUID))
        budget = viewModel.getBudget(budgetUUID)

        budget.observe(this, Observer {
            view.remainingMoneyTextView.setText(it.balance.setScale(2).toPlainString()) // Set the number to always have 2 decimal places

            if (it.balance >= BigDecimal(0)) {
                remainingMoneyTextView.setTextColor(resources.getColor(R.color.colorPositive))
            } else {
                remainingMoneyTextView.setTextColor(resources.getColor(R.color.colorNegative))
            }
        })

        view.addExpenditureButton.setOnClickListener(clickListener)
        view.undoExpenditureButton.setOnClickListener(clickListener)

        viewModel.getExpenditures(budgetUUID).observe(this, Observer {
            adapter.setExpenditures(it)

            // update fragment reference to list of expenditures
            expenditures = it.toMutableList()

            // Scroll to the top to see the most recently added transaction
            expendituresRecyclerView.smoothScrollToPosition(
                    if (adapter.itemCount - 1 < 0) 0
                    else adapter.itemCount - 1
            )
        })

        adapter = ExpenditureAdapter()

        val layoutManager = LinearLayoutManager(context, RecyclerView.VERTICAL, true)
        layoutManager.stackFromEnd = true

        view.expendituresRecyclerView.adapter = adapter
        view.expendituresRecyclerView.layoutManager = layoutManager

        view.incomeSwitch.setOnCheckedChangeListener { _, checked ->
            view.switchTextView.text = if (checked) "Income" else "Expense"
        }
        view.switchTextView.text = "Expense"

        return view
    }
}