package edu.unsw.service;

import edu.unsw.domain.vo.RegistrarDashboardVo;

/**
 * @author WBA Connect
 * @date 14/7/2026 14:56
 * @description Registrar dashboard-related services
 */
public interface RegistrarDashboardService {

    /**
     * Get registrar dashboard statistics
     *
     * @param hospital Hospital filter
     * @param department Department filter
     * @param supervisorId Supervisor id filter
     * @return Registrar dashboard data
     */
    RegistrarDashboardVo getDashboard(String hospital, String department, Long supervisorId);
}
