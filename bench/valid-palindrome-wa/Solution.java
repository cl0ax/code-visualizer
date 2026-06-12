class Solution {
    public boolean isPalindrome(String s) {
        int left = 0;
        int right = s.length() - 1;
        while( left < right){
            char[] charStr = s.toCharArray();
            if(!Character.isLetterOrDigit(charStr[right])){
                right--;
            }
            if(charStr[left] != (charStr[right])){
                return false;
            }

            left++;
            right--;
        }
        return true;
    }
}
