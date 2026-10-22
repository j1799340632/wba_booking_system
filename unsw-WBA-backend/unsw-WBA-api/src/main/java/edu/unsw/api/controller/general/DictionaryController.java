package edu.unsw.api.controller.general;

import com.baomidou.mybatisplus.core.metadata.IPage;
import edu.unsw.core.annotation.RequireRoles;
import edu.unsw.core.constant.RequestPrefixConstant;
import edu.unsw.core.constant.UserRoleKeyConstant;
import edu.unsw.core.domain.pojo.ResultEntity;
import edu.unsw.domain.dto.BaseQueryDto;
import edu.unsw.domain.entity.Department;
import edu.unsw.domain.entity.Hospital;
import edu.unsw.domain.entity.WbaType;
import edu.unsw.domain.vo.DictionaryVo;
import edu.unsw.service.DictionaryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * @author 徐一杰 YijieXu Bubble
 * @date 24/7/2026 21:12
 * @description Base data dictionary management
 */
@RestController
@RequestMapping(RequestPrefixConstant.GENERAL_PREFIX + "/dictionaryManage")
@Tag(name = "Base data dictionary management")
public class DictionaryController {
    private final DictionaryService dictionaryService;

    public DictionaryController(DictionaryService dictionaryService) {
        this.dictionaryService = dictionaryService;
    }

    @GetMapping("/getHospitalList")
    @Operation(summary = "Get hospital list")
    public ResultEntity<IPage<DictionaryVo>> getHospitalList(BaseQueryDto baseQueryDto, String name) {
        return ResultEntity.success("Get hospital list successfully", dictionaryService.getHospitalList(baseQueryDto, name));
    }

    @GetMapping("/getEnabledHospitals")
    @Operation(summary = "Get enabled hospitals")
    public ResultEntity<List<Hospital>> getEnabledHospitals() {
        return ResultEntity.success("Get enabled hospitals successfully", dictionaryService.getEnabledHospitals());
    }

    @PostMapping("/saveHospital")
    @Operation(summary = "Save a hospital")
    @RequireRoles({UserRoleKeyConstant.SUPER_ADMIN, UserRoleKeyConstant.ADMIN})
    public ResultEntity<Boolean> saveHospital(@RequestBody Hospital hospital) {
        return ResultEntity.success("Save the hospital successfully", dictionaryService.saveHospital(hospital));
    }

    @GetMapping("/getDepartmentList")
    @Operation(summary = "Get department list")
    public ResultEntity<IPage<DictionaryVo>> getDepartmentList(BaseQueryDto baseQueryDto, String name) {
        return ResultEntity.success("Get department list successfully", dictionaryService.getDepartmentList(baseQueryDto, name));
    }

    @GetMapping("/getEnabledDepartments")
    @Operation(summary = "Get enabled departments")
    public ResultEntity<List<Department>> getEnabledDepartments() {
        return ResultEntity.success("Get enabled departments successfully", dictionaryService.getEnabledDepartments());
    }

    @PostMapping("/saveDepartment")
    @Operation(summary = "Save a department")
    @RequireRoles({UserRoleKeyConstant.SUPER_ADMIN, UserRoleKeyConstant.ADMIN})
    public ResultEntity<Boolean> saveDepartment(@RequestBody Department department) {
        return ResultEntity.success("Save the department successfully", dictionaryService.saveDepartment(department));
    }

    @GetMapping("/getWbaTypeList")
    @Operation(summary = "Get wba type list")
    public ResultEntity<IPage<DictionaryVo>> getWbaTypeList(BaseQueryDto baseQueryDto, String name) {
        return ResultEntity.success("Get wba type list successfully", dictionaryService.getWbaTypeList(baseQueryDto, name));
    }

    @GetMapping("/getEnabledWbaTypes")
    @Operation(summary = "Get enabled wba types")
    public ResultEntity<List<WbaType>> getEnabledWbaTypes() {
        return ResultEntity.success("Get enabled wba types successfully", dictionaryService.getEnabledWbaTypes());
    }

    @PostMapping("/saveWbaType")
    @Operation(summary = "Save a wba type")
    @RequireRoles({UserRoleKeyConstant.SUPER_ADMIN, UserRoleKeyConstant.ADMIN})
    public ResultEntity<Boolean> saveWbaType(@RequestBody WbaType wbaType) {
        return ResultEntity.success("Save the wba type successfully", dictionaryService.saveWbaType(wbaType));
    }

    @PostMapping("/changeHospitalStatus")
    @Operation(summary = "Change the hospital status")
    @RequireRoles({UserRoleKeyConstant.SUPER_ADMIN, UserRoleKeyConstant.ADMIN})
    public ResultEntity<Integer> changeHospitalStatus(@NotNull(message = "Id cannot be null") Long id, @NotNull(message = "Status cannot be null") Boolean targetStatus) {
        return ResultEntity.success("Change the hospital status successfully", dictionaryService.changeHospitalStatus(id, targetStatus));
    }

    @PostMapping("/changeDepartmentStatus")
    @Operation(summary = "Change the department status")
    @RequireRoles({UserRoleKeyConstant.SUPER_ADMIN, UserRoleKeyConstant.ADMIN})
    public ResultEntity<Integer> changeDepartmentStatus(@NotNull(message = "Id cannot be null") Long id, @NotNull(message = "Status cannot be null") Boolean targetStatus) {
        return ResultEntity.success("Change the department status successfully", dictionaryService.changeDepartmentStatus(id, targetStatus));
    }

    @PostMapping("/changeWbaTypeStatus")
    @Operation(summary = "Change the wab type status")
    @RequireRoles({UserRoleKeyConstant.SUPER_ADMIN, UserRoleKeyConstant.ADMIN})
    public ResultEntity<Integer> changeWbaTypeStatus(@NotNull(message = "Id cannot be null") Long id, @NotNull(message = "Status cannot be null") Boolean targetStatus) {
        return ResultEntity.success("Change the wab type status successfully", dictionaryService.changeWbaTypeStatus(id, targetStatus));
    }

    @PostMapping("/delHospital")
    @Operation(summary = "Del Hospital")
    @RequireRoles({UserRoleKeyConstant.SUPER_ADMIN, UserRoleKeyConstant.ADMIN})
    public ResultEntity<Integer> delHospital(@NotNull(message = "Id cannot be null") Long id) {
        return ResultEntity.success("Del Hospital successfully", dictionaryService.delHospital(id));
    }

    @PostMapping("/delDepartment")
    @Operation(summary = "Del department")
    @RequireRoles({UserRoleKeyConstant.SUPER_ADMIN, UserRoleKeyConstant.ADMIN})
    public ResultEntity<Integer> delDepartment(@NotNull(message = "Id cannot be null") Long id) {
        return ResultEntity.success("Del department successfully", dictionaryService.delDepartment(id));
    }

    @PostMapping("/delWbaType")
    @Operation(summary = "Del wba type")
    @RequireRoles({UserRoleKeyConstant.SUPER_ADMIN, UserRoleKeyConstant.ADMIN})
    public ResultEntity<Integer> delWbaType(@NotNull(message = "Id cannot be null") Long id) {
        return ResultEntity.success("Del wba type successfully", dictionaryService.delWbaType(id));
    }

    @GetMapping("/downloadDicImportTemplate")
    @Operation(summary = "Download a dic import template")
    @RequireRoles({UserRoleKeyConstant.SUPER_ADMIN, UserRoleKeyConstant.ADMIN})
    public void downloadDicImportTemplate(HttpServletResponse response) {
        dictionaryService.downloadDicImportTemplate(response);
    }

    @PostMapping("/importBatchHospitals")
    @Operation(summary = "Upload a file and parse to add batch hospitals")
    @RequireRoles({UserRoleKeyConstant.SUPER_ADMIN, UserRoleKeyConstant.ADMIN})
    public ResultEntity<Integer> importBatchHospitals(@RequestPart("file") MultipartFile multipartFile) {
        return ResultEntity.success("Import batch hospitals successfully", dictionaryService.importBatchHospitals(multipartFile));
    }

    @PostMapping("/importBatchDepartments")
    @Operation(summary = "Upload a file and parse to add batch departments")
    @RequireRoles({UserRoleKeyConstant.SUPER_ADMIN, UserRoleKeyConstant.ADMIN})
    public ResultEntity<Integer> importBatchDepartments(@RequestPart("file") MultipartFile multipartFile) {
        return ResultEntity.success("Import batch departments successfully", dictionaryService.importBatchDepartments(multipartFile));
    }

    @PostMapping("/importBatchWbaTypes")
    @Operation(summary = "Upload a file and parse to add batch wba types")
    @RequireRoles({UserRoleKeyConstant.SUPER_ADMIN, UserRoleKeyConstant.ADMIN})
    public ResultEntity<Integer> importBatchWbaTypes(@RequestPart("file") MultipartFile multipartFile) {
        return ResultEntity.success("Import batch wba types successfully", dictionaryService.importBatchWbaTypes(multipartFile));
    }
}
