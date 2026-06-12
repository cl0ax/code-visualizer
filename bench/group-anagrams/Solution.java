class Solution {
    public List<List<String>> groupAnagrams(String[] strs) {
        Map<String, List<String>> count = new HashMap<>();
        for(String str : strs){
            char[] arr = str.toCharArray();
            Arrays.sort(arr);
            String strSignature = new String(arr);
            count.putIfAbsent(strSignature, new ArrayList<>());
            count.get(strSignature).add(str);
        }
        return new ArrayList<>(count.values());
    }
}
