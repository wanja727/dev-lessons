package lab.devlessons.concurrency;

/**
 * 별도 프로세스(새 JVM)로 실행되는 프로그램. 자기 static 변수를 100으로 바꾸고 PID와 값을 출력한다.
 * 부모 프로세스의 같은 static 변수에는 영향이 없다는 것을 보여주기 위해 쓴다.
 */
public class ChildProcessMain {

    public static int counter = 0;

    public static void main(String[] args) {
        counter = 100;
        System.out.println("pid=" + ProcessHandle.current().pid() + " counter=" + counter);
    }
}
