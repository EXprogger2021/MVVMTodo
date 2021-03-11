package com.codinginflow.mvvmtodo.ui.tasks

import androidx.hilt.Assisted
import androidx.hilt.lifecycle.ViewModelInject
import androidx.lifecycle.*
import com.codinginflow.mvvmtodo.data.PreferenceManager
import com.codinginflow.mvvmtodo.data.SortOrder
import com.codinginflow.mvvmtodo.data.Task
import com.codinginflow.mvvmtodo.data.TaskDao
import com.codinginflow.mvvmtodo.ui.ADD_TASK_RESULT_OK
import com.codinginflow.mvvmtodo.ui.EDIT_TASK_RESULT_OK
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class TasksViewModel @ViewModelInject constructor(
        private val taskDao: TaskDao,
        private val preferenceManager: PreferenceManager,
        @Assisted private val state: SavedStateHandle
): ViewModel() {
    val searchQuery = state.getLiveData("searchQuery", "")

    val preferencesFlow = preferenceManager.preferenceFlow

    private val taskEventChannel = Channel<TaskEvent>()
    val taskEvent = taskEventChannel.receiveAsFlow()

    private val tasksFlow = combine(
            searchQuery.asFlow(),
            preferencesFlow
    ) {query, filterPreferences ->
        Pair(query, filterPreferences)
    }
            .flatMapLatest { (query, filterPreferences) ->
        taskDao.getTask(query, filterPreferences.sortOrder, filterPreferences.hideCompleted)
    }

    val tasks = tasksFlow.asLiveData()

    fun onSortOrderSelected(sortOrder: SortOrder) = viewModelScope.launch {
        preferenceManager.updateSortOrder(sortOrder)
    }

    fun onTaskSelected(task: Task) = viewModelScope.launch {
        taskEventChannel.send(TaskEvent.NavigateToEditTaskScreen(task))
    }

    fun onHideCompletedClick(hideCompleted: Boolean) = viewModelScope.launch {
        preferenceManager.updateHideCompleted(hideCompleted)
    }

    fun onTaskCheckedChanged(task: Task, isChecked: Boolean) = viewModelScope.launch {
        taskDao.update(task.copy(completed = isChecked))
    }

    fun onTaskSwiped(task: Task) = viewModelScope.launch {
        taskDao.delete(task)
        taskEventChannel.send(TaskEvent.ShowUndoDeleteTaskMessage(task))
    }

    fun onUndoDeleteClick(task: Task) = viewModelScope.launch {
        taskDao.insert(task)
    }

    fun onAddNewTaskClick() = viewModelScope.launch {
        taskEventChannel.send(TaskEvent.NavigfateToAddTaskScreen)
    }

    fun onAddEditResult(result: Int) {
        when (result) {
            ADD_TASK_RESULT_OK -> showTaskSavedConfirmationMessage("Task saved")
            EDIT_TASK_RESULT_OK -> showTaskSavedConfirmationMessage("Task updated")
        }
    }

    private fun showTaskSavedConfirmationMessage(message: String) = viewModelScope.launch {
        taskEventChannel.send(TaskEvent.ShowTaskSavedConfirmationMessage(message))
    }

    fun onDeleteAllCompletedClick() = viewModelScope.launch {
        taskEventChannel.send(TaskEvent.NavigateToDeleteAllCompletedDialog)
    }

    sealed class TaskEvent {
        object NavigfateToAddTaskScreen: TaskEvent()
        data class NavigateToEditTaskScreen(val task: Task): TaskEvent()
        data class ShowUndoDeleteTaskMessage(val task: Task): TaskEvent()
        data class ShowTaskSavedConfirmationMessage(val msg: String): TaskEvent()
        object NavigateToDeleteAllCompletedDialog : TaskEvent()
    }

}