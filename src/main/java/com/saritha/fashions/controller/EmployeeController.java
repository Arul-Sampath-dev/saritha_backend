package com.saritha.fashions.controller;

import com.saritha.fashions.model.Employee;
import com.saritha.fashions.service.Interface.EmployeeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/employees")
public class EmployeeController {

    @Autowired
    private EmployeeService employeeService;

    @PostMapping("/add")
    public Employee addEmployee(@RequestBody Employee employee) {
        return employeeService.addEmployee(employee);
    }

    @GetMapping("/{id}")
    public Employee getEmployeeById(@PathVariable String id) {
        return employeeService.getEmployeeById(id);
    }

    @GetMapping("/all")
    public List<Employee> getAllEmployees() {
        return employeeService.getAllEmployees();
    }

    @GetMapping("/shop/{shopId}")
    public List<Employee> getEmployeesByShop(@PathVariable String shopId) {
        return employeeService.getEmployeesByShop(shopId);
    }

    @PostMapping("/assign/{employeeId}/{shopId}")
    public Employee assignEmployeeToShop(@PathVariable String employeeId, @PathVariable String shopId) {
        return employeeService.assignEmployeeToShop(employeeId, shopId);
    }

    @DeleteMapping("/remove/{employeeId}/{shopId}")
    public Employee removeEmployeeFromShop(@PathVariable String employeeId, @PathVariable String shopId) {
        return employeeService.removeEmployeeFromShop(employeeId, shopId);
    }

    @DeleteMapping("/delete/{id}")
    public void deleteEmployee(@PathVariable String id) {
        employeeService.deleteEmployee(id);
    }
}
