package com.saritha.fashions.service;

import com.saritha.fashions.model.Employee;
import com.saritha.fashions.model.Shop;
import com.saritha.fashions.repository.EmployeeRepository;
import com.saritha.fashions.repository.ShopRepository;

import com.saritha.fashions.service.Interface.EmployeeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class EmployeeServiceImpl implements EmployeeService {

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private ShopRepository shopRepository;

    @Override
    public Employee addEmployee(Employee employee) {
        return employeeRepository.save(employee);
    }

    @Override
    public Employee getEmployeeById(String id) {
        return employeeRepository.findById(id).orElse(null);
    }

    @Override
    public List<Employee> getAllEmployees() {
        return employeeRepository.findAll();
    }

    @Override
    public List<Employee> getEmployeesByShop(String shopId) {
        return employeeRepository.findByAssignedShopsId(shopId);
    }

    @Override
    public Employee assignEmployeeToShop(String employeeId, String shopId) {
        Optional<Employee> empOpt = employeeRepository.findById(employeeId);
        Optional<Shop> shopOpt = shopRepository.findById(shopId);

        if (empOpt.isPresent() && shopOpt.isPresent()) {
            Employee employee = empOpt.get();
            Shop shop = shopOpt.get();

            if (!employee.getAssignedShops().contains(shop)) {
                employee.getAssignedShops().add(shop);
//                shop.getEmployees().add(employee);
                employeeRepository.save(employee);
                shopRepository.save(shop);
            }
            return employee;
        }
        return null;
    }

    @Override
    public Employee removeEmployeeFromShop(String employeeId, String shopId) {
        Optional<Employee> empOpt = employeeRepository.findById(employeeId);
        Optional<Shop> shopOpt = shopRepository.findById(shopId);

        if (empOpt.isPresent() && shopOpt.isPresent()) {
            Employee employee = empOpt.get();
            Shop shop = shopOpt.get();

            employee.getAssignedShops().remove(shop);
            shop.getEmployees().remove(employee);

            employeeRepository.save(employee);
            shopRepository.save(shop);
            return employee;
        }
        return null;
    }

    @Override
    public void deleteEmployee(String id) {
        employeeRepository.deleteById(id);
    }
}

