const AssessmentSlotRepeatTypeEnum = {
    NO_REPEAT: { code: 0, typeName: "NO_REPEAT" },
    DAILY: { code: 1, typeName: "DAILY" },
    WEEKLY: { code: 2, typeName: "WEEKLY" },
    MONTHLY: { code: 3, typeName: "MONTHLY" },
    YEARLY: { code: 4, typeName: "YEARLY" },
};

const codeMap = {
    0: AssessmentSlotRepeatTypeEnum.NO_REPEAT,
    1: AssessmentSlotRepeatTypeEnum.DAILY,
    2: AssessmentSlotRepeatTypeEnum.WEEKLY,
    3: AssessmentSlotRepeatTypeEnum.MONTHLY,
    4: AssessmentSlotRepeatTypeEnum.YEARLY,
};

function getEnumByCode(code) {
    return codeMap[code];
}

export { getEnumByCode };
export default AssessmentSlotRepeatTypeEnum;
