package com.nodare.geosec.presentation.equipment

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nodare.geosec.data.model.Equipment
import com.nodare.geosec.data.repository.EquipmentRepository
import com.nodare.geosec.util.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class EquipmentViewModel @Inject constructor(
    private val equipmentRepository: EquipmentRepository
) : ViewModel() {

    private val _equipment = MutableLiveData<List<Equipment>>()
    val equipment: LiveData<List<Equipment>> = _equipment

    private val _operationState = MutableLiveData<Resource<Unit>>()
    val operationState: LiveData<Resource<Unit>> = _operationState

    init {
        observeEquipment()
    }

    private fun observeEquipment() {
        viewModelScope.launch {
            equipmentRepository.observeEquipment()
                .catch { e ->
                    _equipment.value = emptyList()
                }
                .collect { items ->
                    _equipment.value = items
                }
        }
    }

    fun addEquipment(equipment: Equipment) {
        _operationState.value = Resource.Loading
        viewModelScope.launch {
            when (val result = equipmentRepository.addEquipment(equipment)) {
                is Resource.Success -> _operationState.value = Resource.Success(Unit)
                is Resource.Error -> _operationState.value = Resource.Error(result.message)
                is Resource.Loading -> {}
            }
        }
    }

    fun updateEquipment(documentId: String, name: String, category: String, status: String) {
        _operationState.value = Resource.Loading
        viewModelScope.launch {
            _operationState.value = equipmentRepository.updateEquipment(
                documentId, name, category, status
            )
        }
    }
}
