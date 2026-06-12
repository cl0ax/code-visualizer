class Solution {
    public boolean isPalindrome(String s) {
        int left = 0;
        int right = s.length() - 1;

        char[] charStr = s.toCharArray();

        while( left < right){
            while( left < right && !Character.isLetterOrDigit(charStr[left])){
                left++;
            }
            while( left < right && !Character.isLetterOrDigit(charStr[right])){
                right--;
            }
            if(Character.toLowerCase(charStr[left]) != Character.toLowerCase((charStr[right]))){
                return false;
            }

            left++;
            right--;
        }
        return true;
    }
}
