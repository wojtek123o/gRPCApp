public class DataRecord {
    private String id;
    private String employeename;
    private String filename;
    private int age;

    public DataRecord(String id, String employeename, String filename, int age) {
        this.id = id;
        this.employeename = employeename;
        this.filename = filename;
        this.age = age;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getEmployeename() {
        return employeename;
    }

    public void setEmployeename(String employeename) {
        this.employeename = employeename;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public int getAge() {
        return age;
    }

    public void setAge(int age) {
        this.age = age;
    }
}
