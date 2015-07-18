package tosca.nodes;

public abstract class Root {

    protected String name;

    public void setName(String name) {
        this.name = name;
    }

    public String getName() {

        return name;
    }

    public void create() {
    }

    public void configure() {
    }

    public void start() {
    }

    public void stop() {
    }

    public void delete() {
    }
}
