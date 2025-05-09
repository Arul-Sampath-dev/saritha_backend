package com.saritha.fashions.service.Interface;

import com.saritha.fashions.model.Employee;
import java.util.List;

public interface EmployeeService {
    Employee addEmployee(Employee employee);
    Employee getEmployeeById(String id);
    List<Employee> getAllEmployees();
    List<Employee> getEmployeesByShop(String shopId);
    Employee assignEmployeeToShop(String employeeId, String shopId);
    Employee removeEmployeeFromShop(String employeeId, String shopId);
    void deleteEmployee(String id);
}