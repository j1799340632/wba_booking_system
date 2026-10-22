package edu.unsw.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import edu.unsw.core.config.exception.BusinessException;
import edu.unsw.dao.mapper.DepartmentMapper;
import edu.unsw.dao.mapper.HospitalMapper;
import edu.unsw.dao.mapper.UserMapper;
import edu.unsw.dao.mapper.WbaTypeMapper;
import edu.unsw.domain.dto.BaseQueryDto;
import edu.unsw.domain.entity.Department;
import edu.unsw.domain.entity.Hospital;
import edu.unsw.domain.entity.User;
import edu.unsw.domain.entity.WbaType;
import edu.unsw.domain.vo.DictionaryVo;
import edu.unsw.service.DictionaryService;
import edu.unsw.service.utils.FastExcelUtils;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author 徐一杰 YijieXu Bubble
 * @date 24/7/2026 21:33
 * @description
 */
@Service
public class DictionaryServiceImpl implements DictionaryService {
    private final Logger logger = LoggerFactory.getLogger(DictionaryServiceImpl.class);

    private final HospitalMapper hospitalMapper;
    private final DepartmentMapper departmentMapper;
    private final WbaTypeMapper wbaTypeMapper;
    private final UserMapper userMapper;

    public DictionaryServiceImpl(HospitalMapper hospitalMapper, DepartmentMapper departmentMapper, WbaTypeMapper wbaTypeMapper, UserMapper userMapper) {
        this.hospitalMapper = hospitalMapper;
        this.departmentMapper = departmentMapper;
        this.wbaTypeMapper = wbaTypeMapper;
        this.userMapper = userMapper;
    }

    @Override
    public IPage<DictionaryVo> getHospitalList(BaseQueryDto baseQueryDto, String name) {
        IPage<Hospital> hospitalPage = hospitalMapper.selectPage(
                new Page<>(baseQueryDto.getPageNum(), baseQueryDto.getPageQuantity()),
                Wrappers.<Hospital>lambdaQuery()
                        .like(StringUtils.hasText(name), Hospital::getName, name)
                        .orderByDesc(Hospital::getId)
        );

        List<Hospital> hospitalList = hospitalPage.getRecords();

        if (hospitalList.isEmpty()) {
            return new Page<>();
        }

        Set<Long> creatorSet = hospitalList.stream().map(Hospital::getCreator).collect(Collectors.toSet());

        List<User> userList = userMapper.selectList(
                Wrappers.<User>lambdaQuery()
                        .in(User::getId, creatorSet)
                        .select(User::getId, User::getName)
        );

        return hospitalPage.convert(item -> {
            DictionaryVo dictionaryVo = new DictionaryVo();
            BeanUtils.copyProperties(item, dictionaryVo);
            User creator = userList.stream().filter(user -> user.getId().equals(item.getCreator())).findFirst().orElse(null);
            dictionaryVo.setCreatorName(creator != null ? creator.getName() : null);
            return dictionaryVo;
        });
    }

    @Override
    public List<Hospital> getEnabledHospitals() {
        return hospitalMapper.selectList(
                Wrappers.<Hospital>lambdaQuery()
                        .eq(Hospital::getIsEnable, true)
                        .select(Hospital::getId, Hospital::getName)
        );
    }

    @Override
    public IPage<DictionaryVo> getDepartmentList(BaseQueryDto baseQueryDto, String name) {
        Page<Department> departmentPage = departmentMapper.selectPage(
                new Page<>(baseQueryDto.getPageNum(), baseQueryDto.getPageQuantity()),
                Wrappers.<Department>lambdaQuery()
                        .like(StringUtils.hasText(name), Department::getName, name)
                        .orderByDesc(Department::getId)
        );

        List<Department> departmentList = departmentPage.getRecords();

        if (departmentList.isEmpty()) {
            return new Page<>();
        }

        Set<Long> creatorSet = departmentList.stream().map(Department::getCreator).collect(Collectors.toSet());

        List<User> userList = userMapper.selectList(
                Wrappers.<User>lambdaQuery()
                        .in(User::getId, creatorSet)
                        .select(User::getId, User::getName)
        );

        return departmentPage.convert(item -> {
            DictionaryVo dictionaryVo = new DictionaryVo();
            BeanUtils.copyProperties(item, dictionaryVo);
            User creator = userList.stream().filter(user -> user.getId().equals(item.getCreator())).findFirst().orElse(null);
            dictionaryVo.setCreatorName(creator != null ? creator.getName() : null);
            return dictionaryVo;
        });
    }

    @Override
    public List<Department> getEnabledDepartments() {
        return departmentMapper.selectList(
                Wrappers.<Department>lambdaQuery()
                        .eq(Department::getIsEnable, true)
                        .select(Department::getId, Department::getName)
        );
    }

    @Override
    public IPage<DictionaryVo> getWbaTypeList(BaseQueryDto baseQueryDto, String name) {
        Page<WbaType> wbaTypePage = wbaTypeMapper.selectPage(
                new Page<>(baseQueryDto.getPageNum(), baseQueryDto.getPageQuantity()),
                Wrappers.<WbaType>lambdaQuery()
                        .like(StringUtils.hasText(name), WbaType::getName, name)
                        .orderByDesc(WbaType::getId)
        );

        List<WbaType> wbaTypeList = wbaTypePage.getRecords();

        if (wbaTypeList.isEmpty()) {
            return new Page<>();
        }

        Set<Long> creatorSet = wbaTypeList.stream().map(WbaType::getCreator).collect(Collectors.toSet());

        List<User> userList = userMapper.selectList(
                Wrappers.<User>lambdaQuery()
                        .in(User::getId, creatorSet)
                        .select(User::getId, User::getName)
        );

        return wbaTypePage.convert(item -> {
            DictionaryVo dictionaryVo = new DictionaryVo();
            BeanUtils.copyProperties(item, dictionaryVo);
            User creator = userList.stream().filter(user -> user.getId().equals(item.getCreator())).findFirst().orElse(null);
            dictionaryVo.setCreatorName(creator != null ? creator.getName() : null);
            return dictionaryVo;
        });
    }

    @Override
    public List<WbaType> getEnabledWbaTypes() {
        return wbaTypeMapper.selectList(
                Wrappers.<WbaType>lambdaQuery()
                        .eq(WbaType::getIsEnable, true)
                        .select(WbaType::getId, WbaType::getName)
        );
    }

    @Override
    public Boolean saveHospital(Hospital hospital) {
        return hospitalMapper.insertOrUpdate(hospital);
    }

    @Override
    public Boolean saveDepartment(Department department) {
        return departmentMapper.insertOrUpdate(department);
    }

    @Override
    public Boolean saveWbaType(WbaType wbaType) {
        return wbaTypeMapper.insertOrUpdate(wbaType);
    }

    @Override
    public Integer changeHospitalStatus(Long id, Boolean targetStatus) {
        return hospitalMapper.update(
                Wrappers.<Hospital>lambdaUpdate()
                        .eq(Hospital::getId, id)
                        .set(Hospital::getIsEnable, targetStatus)
        );
    }

    @Override
    public Integer changeDepartmentStatus(Long id, Boolean targetStatus) {
        return departmentMapper.update(
                Wrappers.<Department>lambdaUpdate()
                        .eq(Department::getId, id)
                        .set(Department::getIsEnable, targetStatus)
        );
    }

    @Override
    public Integer changeWbaTypeStatus(Long id, Boolean targetStatus) {
        return wbaTypeMapper.update(
                Wrappers.<WbaType>lambdaUpdate()
                        .eq(WbaType::getId, id)
                        .set(WbaType::getIsEnable, targetStatus)
        );
    }

    @Override
    public Integer delHospital(Long id) {
        return hospitalMapper.deleteById(id);
    }

    @Override
    public Integer delDepartment(Long id) {
        return departmentMapper.deleteById(id);
    }

    @Override
    public Integer delWbaType(Long id) {
        return wbaTypeMapper.deleteById(id);
    }

    @Override
    public void downloadDicImportTemplate(HttpServletResponse response) {
        FastExcelUtils.exportExcel(List.of("name"), List.of(), "dic_import_template.xlsx", response);
    }

    @Override
    public Integer importBatchHospitals(MultipartFile multipartFile) {
        int insertCount = 0;

        try (InputStream inputStream = multipartFile.getInputStream()) {
            // Parse to Header-Value map list
            List<Map<String, String>> dataList = FastExcelUtils.readToMapList(inputStream);

            for (Map<String, String> rowMap : dataList) {
                Hospital hospital = new Hospital();
                hospital.setName(rowMap.get("name"));
                Long count = hospitalMapper.selectCount(
                        Wrappers.<Hospital>lambdaQuery()
                                .eq(Hospital::getName, hospital.getName())
                );

                if (count > 0) {
                    logger.warn("Hospital name already exists: {}, skip", hospital.getName());
                    continue;
                }
                hospitalMapper.insert(hospital);
                insertCount ++ ;
            }
        } catch (Exception e) {
            throw new BusinessException(e.getMessage());
        }
        return insertCount;
    }

    @Override
    public Integer importBatchDepartments(MultipartFile multipartFile) {
        int insertCount = 0;

        try (InputStream inputStream = multipartFile.getInputStream()) {
            // Parse to Header-Value map list
            List<Map<String, String>> dataList = FastExcelUtils.readToMapList(inputStream);

            for (Map<String, String> rowMap : dataList) {
                Department department = new Department();
                department.setName(rowMap.get("name"));
                Long count = departmentMapper.selectCount(
                        Wrappers.<Department>lambdaQuery()
                                .eq(Department::getName, department.getName())
                );

                if (count > 0) {
                    logger.warn("Department name already exists: {}, skip", department.getName());
                    continue;
                }
                departmentMapper.insert(department);
                insertCount ++ ;
            }
        } catch (Exception e) {
            throw new BusinessException(e.getMessage());
        }
        return insertCount;
    }

    @Override
    public Integer importBatchWbaTypes(MultipartFile multipartFile) {
        int insertCount = 0;

        try (InputStream inputStream = multipartFile.getInputStream()) {
            // Parse to Header-Value map list
            List<Map<String, String>> dataList = FastExcelUtils.readToMapList(inputStream);

            for (Map<String, String> rowMap : dataList) {
                WbaType wbaType = new WbaType();
                wbaType.setName(rowMap.get("name"));
                Long count = wbaTypeMapper.selectCount(
                        Wrappers.<WbaType>lambdaQuery()
                                .eq(WbaType::getName, wbaType.getName())
                );

                if (count > 0) {
                    logger.warn("Wba type name already exists: {}, skip", wbaType.getName());
                    continue;
                }
                wbaTypeMapper.insert(wbaType);
                insertCount ++ ;
            }
        } catch (Exception e) {
            throw new BusinessException(e.getMessage());
        }
        return insertCount;
    }
}
