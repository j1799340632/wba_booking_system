package edu.unsw.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import edu.unsw.domain.dto.BaseQueryDto;
import edu.unsw.domain.entity.Department;
import edu.unsw.domain.entity.Hospital;
import edu.unsw.domain.entity.WbaType;
import edu.unsw.domain.vo.DictionaryVo;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * @author 徐一杰 YijieXu Bubble
 * @date 24/7/2026 21:32
 * @description Base data dictionary management
 */
public interface DictionaryService {
    /**
     * Get hospitals
     * @param baseQueryDto base query dto
     * @param name name
     * @return List of hospitals
     */
    IPage<DictionaryVo> getHospitalList(BaseQueryDto baseQueryDto, String name);

    /**
     * Get enabled hospitals
     * @return List of enabled hospitals
     */
    List<Hospital> getEnabledHospitals();

    /**
     * Get departments
     * @param baseQueryDto base query dto
     * @param name name
     * @return List of department
     */
    IPage<DictionaryVo> getDepartmentList(BaseQueryDto baseQueryDto, String name);

    /**
     * Get enabled departments
     * @return List of enabled department
     */
    List<Department> getEnabledDepartments();

    /**
     * Get wba types
     * @param baseQueryDto base query dto
     * @param name name
     * @return List of wba types
     */
    IPage<DictionaryVo> getWbaTypeList(BaseQueryDto baseQueryDto, String name);

    /**
     * Get enabled wba types
     * @return List of enabled wba types
     */
    List<WbaType> getEnabledWbaTypes();

    /**
     * Save a hospital
     * @param hospital hospital info
     * @return Is successful?
     */
    Boolean saveHospital(Hospital hospital);

    /**
     * Save a department
     * @param department department info
     * @return Is successful?
     */
    Boolean saveDepartment(Department department);

    /**
     * Save a wbaType
     * @param wbaType wbaType info
     * @return Is successful?
     */
    Boolean saveWbaType(WbaType wbaType);

    /**
     * Change the hospital status
     * @param id id
     * @param targetStatus target status
     * @return Update count
     */
    Integer changeHospitalStatus(Long id, Boolean targetStatus);

    /**
     * Change the department status
     * @param id id
     * @param targetStatus target status
     * @return Update count
     */
    Integer changeDepartmentStatus(Long id, Boolean targetStatus);

    /**
     * Change the wba type status
     * @param id id
     * @param targetStatus target status
     * @return Update count
     */
    Integer changeWbaTypeStatus(Long id, Boolean targetStatus);

    /**
     * Delete a hospital
     * @param id id
     * @return Is successful?
     */
    Integer delHospital(Long id);

    /**
     * Delete a department
     * @param id id
     * @return Is successful?
     */
    Integer delDepartment(Long id);

    /**
     * Delete a wba type
     * @param id id
     * @return Is successful?
     */
    Integer delWbaType(Long id);

    /**
     * Download a dic import template
     * @param response response
     */
    void downloadDicImportTemplate(HttpServletResponse response);

    /**
     * Import batch hospitals
     * @param multipartFile multipartFile
     * @return Import count
     */
    Integer importBatchHospitals(MultipartFile multipartFile);

    /**
     * Import batch departments
     * @param multipartFile multipartFile
     * @return Import count
     */
    Integer importBatchDepartments(MultipartFile multipartFile);

    /**
     * Import batch WbaTypes
     * @param multipartFile multipartFile
     * @return Import count
     */
    Integer importBatchWbaTypes(MultipartFile multipartFile);
}
