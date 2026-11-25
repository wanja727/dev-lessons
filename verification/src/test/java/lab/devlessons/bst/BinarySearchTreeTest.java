package lab.devlessons.bst;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;
import lab.devlessons.support.CiOutput;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 레슨 "이진 탐색 트리"(K8)의 주장을 검증한다. 순수 JUnit 테스트.
 * 레슨의 Node는 균형을 맞추지 않는 이진 탐색 트리다. 깊이가 1만까지 갈 수 있어 넣기와 높이 재기는 반복문으로 한다.
 */
class BinarySearchTreeTest {

    private static final String LESSON = "binary-search-tree";

    static class Node {
        int key;
        Node left;
        Node right;

        Node(int key) {
            this.key = key;
        }
    }

    static Node insert(Node root, int key) {
        Node fresh = new Node(key);
        if (root == null) return fresh;
        Node n = root;
        while (true) {
            if (key < n.key) {
                if (n.left == null) { n.left = fresh; return root; }
                n = n.left;
            } else if (key > n.key) {
                if (n.right == null) { n.right = fresh; return root; }
                n = n.right;
            } else {
                return root;
            }
        }
    }

    static Node build(int... keys) {
        Node root = null;
        for (int k : keys) root = insert(root, k);
        return root;
    }

    /** 높이: 뿌리부터 가장 깊은 노드까지의 노드 수. 반복문(너비 우선)으로 잰다 */
    static int height(Node root) {
        if (root == null) return 0;
        List<Node> level = List.of(root);
        int h = 0;
        while (!level.isEmpty()) {
            h++;
            List<Node> next = new ArrayList<>();
            for (Node n : level) {
                if (n.left != null) next.add(n.left);
                if (n.right != null) next.add(n.right);
            }
            level = next;
        }
        return h;
    }

    /** 찾을 때 거친 노드의 키 */
    static List<Integer> path(Node n, int key) {
        List<Integer> visited = new ArrayList<>();
        while (n != null) {
            visited.add(n.key);
            if (key == n.key) break;
            n = key < n.key ? n.left : n.right;
        }
        return visited;
    }

    static void inorder(Node n, StringBuilder out) {
        if (n == null) return;
        inorder(n.left, out);
        out.append(n.key).append(' ');
        inorder(n.right, out);
    }

    @Test
    @DisplayName("4, 2, 6, 1, 3, 5, 7 순서는 높이 3, 1~7 차례로는 높이 7")
    void insertOrderHeight() {
        Node a = build(4, 2, 6, 1, 3, 5, 7);
        Node b = build(1, 2, 3, 4, 5, 6, 7);
        Node c = build(7, 6, 5, 4, 3, 2, 1);

        assertThat(height(a)).isEqualTo(3);
        assertThat(height(b)).isEqualTo(7);
        assertThat(height(c)).isEqualTo(7);
        assertThat(path(a, 7)).hasSize(3);
        assertThat(path(b, 7)).hasSize(7);
        assertThat(path(c, 1)).hasSize(7);
        assertThat(path(a, 5)).containsExactly(4, 6, 5);

        CiOutput.record(LESSON, "predict", """
                a (4, 2, 6, 1, 3, 5, 7): 높이 %d, 7을 찾을 때 거친 노드 %s
                b (1, 2, 3, 4, 5, 6, 7): 높이 %d, 7을 찾을 때 거친 노드 %s""".formatted(
                height(a), path(a, 7), height(b), path(b, 7)));
    }

    @Test
    @DisplayName("중위 순회는 넣은 순서와 상관없이 정렬된 순서로 출력한다")
    void inorderSorted() {
        Node t = build(50, 30, 70, 20, 40);
        StringBuilder out = new StringBuilder();
        inorder(t, out);
        assertThat(out.toString().trim()).isEqualTo("20 30 40 50 70");

        List<Integer> keys = new ArrayList<>();
        for (int i = 0; i < 1000; i++) keys.add(i);
        Collections.shuffle(keys, new Random(7));
        Node r = null;
        for (int k : keys) r = insert(r, k);
        StringBuilder all = new StringBuilder();
        inorder(r, all);
        StringBuilder expected = new StringBuilder();
        for (int i = 0; i < 1000; i++) expected.append(i).append(' ');
        assertThat(all.toString()).isEqualTo(expected.toString());

        CiOutput.record(LESSON, "inorder", "50, 30, 70, 20, 40 순서로 넣고 print → " + out.toString().trim());
    }

    @Test
    @DisplayName("1부터 1만까지 차례로 넣으면 높이 1만, 무작위 순서면 수십")
    void sortedVsRandom() {
        Node sorted = null;
        for (int i = 1; i <= 10_000; i++) sorted = insert(sorted, i);

        List<Integer> keys = new ArrayList<>();
        for (int i = 1; i <= 10_000; i++) keys.add(i);
        Collections.shuffle(keys, new Random(42));
        Node random = null;
        for (int k : keys) random = insert(random, k);

        int hs = height(sorted);
        int hr = height(random);
        assertThat(hs).isEqualTo(10_000);
        assertThat(hr).isLessThan(100);

        CiOutput.record(LESSON, "height", """
                1부터 1만까지 차례로 넣은 트리의 높이: %d
                같은 1만 개를 무작위 순서로 넣은 트리의 높이: %d""".formatted(hs, hr));
    }

    @Test
    @DisplayName("1부터 100만까지 차례로 넣은 TreeMap의 get은 비교 40번을 넘지 않는다")
    void treeMapStaysBalanced() {
        AtomicInteger count = new AtomicInteger();
        Comparator<Integer> counting = (x, y) -> {
            count.incrementAndGet();
            return Integer.compare(x, y);
        };
        TreeMap<Integer, Integer> map = new TreeMap<>(counting);
        for (int i = 1; i <= 1_000_000; i++) map.put(i, i);

        int max = 0;
        for (int key : new int[] {1, 2, 500_000, 999_999, 1_000_000, 1_000_001, 123_457}) {
            count.set(0);
            map.get(key);
            max = Math.max(max, count.get());
        }
        count.set(0);
        map.get(1_000_000);
        int last = count.get();
        assertThat(max).isLessThanOrEqualTo(40);

        CiOutput.record(LESSON, "treemap", """
                1부터 100만까지 차례로 put한 TreeMap
                get(1_000_000)의 키 비교: %d번
                키 일곱 개를 get해서 가장 많았던 비교: %d번""".formatted(last, max));
    }

    @Test
    @DisplayName("floorKey, ceilingKey, subMap")
    void navigation() {
        TreeMap<Integer, String> byPrice = new TreeMap<>();
        byPrice.put(12_000, "머그컵");
        byPrice.put(35_000, "텀블러");
        byPrice.put(8_000, "컵받침");
        byPrice.put(27_000, "드리퍼");

        assertThat(byPrice.floorKey(30_000)).isEqualTo(27_000);
        assertThat(byPrice.ceilingKey(13_000)).isEqualTo(27_000);
        assertThat(byPrice.subMap(10_000, 30_000)).containsOnlyKeys(12_000, 27_000);
        assertThat(byPrice.firstKey()).isEqualTo(8_000);

        CiOutput.record(LESSON, "navigation", """
                byPrice → %s
                floorKey(30_000) → %d
                subMap(10_000, 30_000) → %s""".formatted(
                byPrice, byPrice.floorKey(30_000), byPrice.subMap(10_000, 30_000)));
    }

    static class Sku {
        final String code;

        Sku(String code) {
            this.code = code;
        }
    }

    @Test
    @DisplayName("비교할 수 없는 키는 ClassCastException, null 키는 NullPointerException")
    void keyRequirements() {
        TreeMap<Object, String> map = new TreeMap<>();
        assertThatThrownBy(() -> map.put(new Sku("A"), "x")).isInstanceOf(ClassCastException.class);
        TreeMap<Integer, String> nums = new TreeMap<>();
        nums.put(1, "a");
        assertThatThrownBy(() -> nums.put(null, "x")).isInstanceOf(NullPointerException.class);
    }

    record Product(String name, int price) {
    }

    @Test
    @DisplayName("가격으로만 비교하는 TreeSet은 같은 가격의 상품을 하나로 본다")
    void comparatorEquality() {
        Set<Product> products = new TreeSet<>(Comparator.comparingInt(Product::price));
        products.add(new Product("머그컵", 12000));
        products.add(new Product("텀블러", 12000));

        Set<Product> fixed = new TreeSet<>(
                Comparator.comparingInt(Product::price).thenComparing(Product::name));
        fixed.add(new Product("머그컵", 12000));
        fixed.add(new Product("텀블러", 12000));

        TreeMap<Product, Integer> stock = new TreeMap<>(Comparator.comparingInt(Product::price));
        stock.put(new Product("머그컵", 12000), 5);
        stock.put(new Product("텀블러", 12000), 3);

        assertThat(products).hasSize(1);
        assertThat(products.iterator().next().name()).isEqualTo("머그컵");
        assertThat(fixed).hasSize(2);
        assertThat(stock).hasSize(1);
        assertThat(stock.firstKey().name()).isEqualTo("머그컵");
        assertThat(stock.firstEntry().getValue()).isEqualTo(3);

        CiOutput.record(LESSON, "priceSet", """
                가격으로만 비교: size %d → %s
                이름으로 한 번 더 비교: size %d → %s

                TreeMap에 같은 가격의 두 상품을 put: %s""".formatted(
                products.size(), products, fixed.size(), fixed, stock));
    }

    @Test
    @DisplayName("1, 2, 3 한 줄을 왼쪽으로 한 번 회전하면 2가 뿌리, 1과 3이 자식")
    void rotation() {
        Node line = build(1, 2, 3);
        assertThat(height(line)).isEqualTo(3);
        Node root = line;
        Node pivot = root.right;
        root.right = pivot.left;
        pivot.left = root;
        assertThat(pivot.key).isEqualTo(2);
        assertThat(pivot.left.key).isEqualTo(1);
        assertThat(pivot.right.key).isEqualTo(3);
        assertThat(height(pivot)).isEqualTo(2);
        StringBuilder out = new StringBuilder();
        inorder(pivot, out);
        assertThat(out.toString().trim()).isEqualTo("1 2 3");
    }
}
