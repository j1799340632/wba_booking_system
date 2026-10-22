import {useState, useEffect} from 'react';
import './index.css';
import {Loading} from "@/components/Loading/index.jsx";
import BaseModal from "@/components/BaseModal/index.jsx";
import {notify} from "@/utils/notify.js";
import {useConfirmStore} from "@/config/store/useConfirmStore.js";
import {addRole, changeRoleStatus, delRole, editRole, getRoleList} from "@/config/request/api/userRoleApi.js";

const RoleManage = () => {
    // List data and loading states
    const [roleList, setRoleList] = useState([]);

    // Filter toolbar states
    const [pageNum, setPageNum] = useState(1);
    const [pageQuantity, setPageQuantity] = useState(10);
    const [total, setTotal] = useState(0);
    const [totalPage, setTotalPage] = useState(1);
    const [searchName, setSearchName] = useState('');
    const [modalOpen, setModalOpen] = useState(false);
    const [isSaving, setIsSaving] = useState(false);
    const [selectedRole, setSelectedRole] = useState({});

    const showConfirm = useConfirmStore(state => state.showConfirm);

    const fetchRoleList = (
        params = {
            pageNum,
            pageQuantity
        }
    ) => {
        Loading.show()
        getRoleList(params)
            .then(data => {
                setPageNum(data.data.current)
                setPageQuantity(data.data.size)
                setTotal(data.data.total)
                setTotalPage(data.data.pages)
                setRoleList(data.data.records);
            })
            .finally(() => Loading.hide());
    };

    useEffect(() => {
        fetchRoleList();
    }, []);

    const handleSearch = () => {
        const params = {
            pageNum: 1,
            pageQuantity,
            name: searchName
        }
        fetchRoleList(params)
    }

    const handleReset = () => {
        setSearchName('');
        const params = {
            pageNum: 1,
            pageQuantity,
        }
        fetchRoleList(params);
    }

    const handlePageChange = newPageNum => {
        setPageNum(newPageNum);
        const params = {
            pageNum: newPageNum,
            pageQuantity,
            name: searchName
        }
        fetchRoleList(params)
    }

    const handleChangeStatus = (id, targetStatus) => {
        changeRoleStatus(id, targetStatus).then(() => {
            notify.success("Change status successfully")
            fetchRoleList()
        })
    }

    const handleDel = (id) => {
        showConfirm(
            'Reminder',
            'Are you sure to confirm?',
            () => {
                delRole(id).then(() => {
                    notify.success("Delete successfully")
                    fetchRoleList()
                })
            }
        )
    }

    const openModal = data => {
        setModalOpen(true);
        setSelectedRole(data)
    };

    const closeModal = () => {
        setModalOpen(false);
        setSelectedRole({});
    };

    const submitEdit = (e) => {
        e.preventDefault();

        setIsSaving(true)

        const data = {
            id: selectedRole?.id,
            permissionPriority: e.target.elements.permissionPriority.value,
            roleKey: e.target.elements.roleKey.value,
            roleName: e.target.elements.roleName.value,
        }

        if (data.id) {
            editRole(data).then(() => {
                notify.success("Edit the role successfully")
                closeModal()
                fetchRoleList()
            }).finally(() => {
                setIsSaving(false)
            })
        } else {
            addRole(data).then(() => {
                notify.success("Add the role successfully")
                closeModal()
                fetchRoleList()
            }).finally(() => {
                setIsSaving(false)
            })
        }
    };

    return (
        <div className="dictionary-page-container">
            {/* Header Section */}
            <div className="dictionary-page-header">
                <h1>Roles</h1>
                <p className="dictionary-record-count">{total} roles</p>
            </div>

            {/* Filter Toolbar */}
            <div className="dictionary-filter-row">
                <div className="dictionary-filter-group">
                    <span className="dictionary-filter-icon">🔍</span>
                    <input
                        type="text"
                        placeholder="Search name..."
                        value={searchName}
                        onChange={(e) => setSearchName(e.target.value)}
                        className="dictionary-input"
                    />
                </div>
                <div className="dictionary-btn-group">
                    <button onClick={handleSearch} className="dictionary-btn-search">Search</button>
                    <button onClick={handleReset} className="dictionary-btn-reset">Reset</button>
                    <button onClick={() => openModal({})} className="dictionary-btn-add">Add</button>
                </div>
            </div>

            {/* Data Table */}
            <div className="dictionary-table-card">
                <table className="dictionary-compact-table">
                    <thead>
                    <tr>
                        <th>ID</th>
                        <th>PermissionPriority</th>
                        <th>RoleKey</th>
                        <th>RoleName</th>
                        <th>Creator</th>
                        <th>Status</th>
                        <th>Create Time</th>
                        <th>Edit Time</th>
                        <th className={'dictionary-actionTh'}>Action</th>
                    </tr>
                    </thead>
                    <tbody>
                    {roleList.length > 0 ? (
                        roleList.map(role => (
                            <tr key={role.id} className={role.isEnable ? '' : 'dictionary-trDisable'}>
                                <td>{role.id}</td>
                                <td>{role.permissionPriority || 'N/A'}</td>
                                <td>{role.roleKey || 'N/A'}</td>
                                <td>{role.roleName || 'N/A'}</td>
                                <td>{role.creatorName || 'N/A'}</td>
                                <td>
                                    <span className={`dictionary-status-pill ${role.isEnable ? 'enable' : 'disable'}`}>
                                        {role.isEnable ? 'Enable' : 'Disable'}
                                    </span>
                                </td>
                                <td>{role.createTime || 'N/A'}</td>
                                <td>{role.editTime || 'N/A'}</td>
                                <td>
                                    <div className={'dictionary-actionTd'}>
                                        <button
                                            type="button"
                                            role="switch"
                                            className={`dictionary-settings-switch ${role.isEnable ? "on" : "]"}`}
                                            onClick={() => handleChangeStatus(role.id, !role.isEnable)}
                                        >
                                            <span className="dictionary-settings-switch-thumb" />
                                        </button>
                                        <button
                                            className="dictionary-list-button"
                                            onClick={() => openModal(role)}
                                        >
                                            Edit
                                        </button>
                                        <button
                                            className="dictionary-list-button del"
                                            onClick={() => handleDel(role.id)}
                                        >
                                            Del
                                        </button>
                                    </div>
                                </td>
                            </tr>
                        ))
                    ) : (
                        <tr>
                            <td colSpan="6" className="dictionary-no-data">No data found in database.</td>
                        </tr>
                    )}
                    </tbody>
                </table>
            </div>

            {/* Pagination Footer */}
            <div className="dictionary-pagination-bar">
                <button className="dictionary-page-btn" disabled={pageNum === 1} onClick={() => handlePageChange(pageNum - 1)}>Previous</button>
                <span className="dictionary-page-info">Page {pageNum} of {totalPage}</span>
                <button className="dictionary-page-btn" disabled={pageNum === totalPage} onClick={() => handlePageChange(pageNum + 1)}>Next</button>
            </div>

            <BaseModal
                isOpen={modalOpen}
                title="Add/Edit Role"
                onClose={closeModal}
            >
                <form onSubmit={submitEdit} className="dictionary-edit-card">
                    <label className="dictionary-edit-label" htmlFor="edit-role-permission-priority">
                        Permission Priority
                        <input
                            id="edit-role-permission-priority"
                            name="permissionPriority"
                            type="number"
                            min={1}
                            className="dictionary-edit-input"
                            defaultValue={selectedRole?.permissionPriority}
                            required
                        />
                    </label>

                    <label className="dictionary-edit-label" htmlFor="edit-role-key">
                        Role Key
                        <input
                            id="edit-role-key"
                            name="roleKey"
                            type="text"
                            className="dictionary-edit-input"
                            defaultValue={selectedRole?.roleKey}
                            required
                        />
                    </label>

                    <label className="dictionary-edit-label" htmlFor="edit-name">
                        Role Name
                        <input
                            id="edit-name"
                            name="roleName"
                            type="text"
                            className="dictionary-edit-input"
                            defaultValue={selectedRole?.roleName}
                            required
                        />
                    </label>

                    <div className="dictionary-edit-actions">
                        <button
                            type="button"
                            onClick={closeModal}
                            className="dictionary-cancel-button"
                            disabled={isSaving}
                        >
                            Cancel
                        </button>

                        <button
                            type="submit"
                            className="dictionary-submit-button"
                            disabled={isSaving}
                        >
                            {isSaving ? "Saving..." : "Save Changes"}
                        </button>
                    </div>
                </form>
            </BaseModal>
        </div>
    );
};

export default RoleManage;
