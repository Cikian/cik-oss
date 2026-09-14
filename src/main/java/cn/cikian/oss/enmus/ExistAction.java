package cn.cikian.oss.enmus;

/**
 * <p>
 * 文件存在时执行动作
 * </p>
 *
 * @author Cikian
 * @version 1.0
 * @implNote
 * @see <a href="https://www.cikian.cn">https://www.cikian.cn</a>
 * @since 2026/7/21
 */
public enum ExistAction {

    // 覆盖
    COVER("cover"),

    // 获取当前存在的对象URL（默认）
    EXIST("exist"),

    // 跳过（抛出异常）
    SKIP("skip"),
    ;

    private String action;

    ExistAction(String action) {
        this.action = action;
    }

    public String getAction() {
        return action;
    }
}
