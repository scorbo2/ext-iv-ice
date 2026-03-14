package ca.corbett.imageviewer.extensions.ice;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TagListTest {

    @Test
    public void testOf_withEmptyInput_shouldGiveEmptyTagList() {
        // GIVEN input that evaluates to an empty tag list:
        String[] badInput = {
                "",
                null,
                "              ",
                ",,,,,,,,,,",
                " , , ,  , , ,  , , ,"
        };

        for (String input : badInput) {
            // WHEN we try to parse it:
            TagList actual = TagList.of(input);

            // THEN we should indeed get an empty list:
            assertNotNull(actual);
            assertEquals(0, actual.size());
        }
    }

    @Test
    public void testOf_withNonEmptyInput_shouldGiveTagList() {
        // GIVEN a comma-separated input:
        String[] goodInput = {
                "hello,there",
                "hello , there",
                "     hello    ,    there    ",
                ",,,,,,,,,,,,,hello,there,,,,,,,,,,,,,,,"
        };

        for (String input : goodInput) {
            // WHEN we try to parse it:
            TagList actual = TagList.of(input);

            // THEN we should get the expected list:
            assertNotNull(actual);
            assertEquals(2, actual.size());
            assertTrue(actual.hasTag("hello"));
            assertTrue(actual.hasTag("there"));
            assertEquals("hello, there", actual.toString());
        }
    }

    @Test
    public void testAdd_shouldIgnoreCase() {
        // GIVEN tags in various cases:
        String[] mixedCaseTags = {
                "hello,there",
                "Hello,There",
                "HELLO,THERE"
        };

        for (String input : mixedCaseTags) {
            // WHEN we try to parse it:
            List<String> tagList = TagList.of(input).getTags();

            // THEN they should all be lower case:
            assertNotNull(tagList);
            assertEquals(2, tagList.size());
            assertEquals("hello", tagList.get(0));
            assertEquals("there", tagList.get(1));
        }
    }

    @Test
    public void toString_withEmptyList_shouldReturnEmptyString() {
        // GIVEN an empty TagList:
        TagList tagList = new TagList();

        // WHEN we toString() it:
        String actual = tagList.toString();

        // THEN we should see an empty string:
        assertEquals("", actual);
    }

    @Test
    public void toString_withOneTag_shouldNotCommaSeparate() {
        // GIVEN a TagList with only one tag:
        TagList tagList = new TagList();
        tagList.add("hello");

        // WHEN we toString() it:
        String actual = tagList.toString();

        // THEN we shouldn't see any commas:
        assertEquals("hello", actual);
    }

    @Test
    public void toString_withMultipleTags_shouldCommaSeparate() {
        // GIVEN a TagList with multiple tags:
        TagList tagList = new TagList();
        tagList.addAll(List.of("one", "two", "three", "four"));

        // WHEN we toString() it:
        String actual = tagList.toString();

        // THEN we should see a nice comma-separated string:
        assertEquals("one, two, three, four", actual);
    }

    @Test
    public void add_withAllBadChars_shouldNotAdd() {
        // GIVEN input tags with forbidden characters:
        String[] badInput = {
                "{}{}{}{}",
                " ,,,,,,,,,,,,,|||",
                "|",
                " | "
        };

        for (String input : badInput) {
            // WHEN we try to add them to a TagList:
            TagList list = new TagList();
            list.add(input);

            // THEN we should see that the tag was stripped and not added:
            assertEquals(0, list.size());
        }
    }

    @Test
    public void add_withSomeBadChars_shouldStripAndAdd() {
        // GIVEN input tags with some forbidden characters:
        String[] badInput = {
                "{hello}",
                "h,e,l,l,o",
                "hello|",
                " he|llo "
        };

        for (String input : badInput) {
            // WHEN we try to add them to a TagList:
            TagList list = new TagList();
            list.add(input);

            // THEN we should see that the tag was stripped during the add:
            assertEquals(1, list.size());
            assertTrue(list.hasTag("hello"));
        }
    }

    @Test
    public void replace_withNoMatch_shouldDoNothing() {
        // GIVEN a tag with no text to be replaced:
        TagList tagList = TagList.of("hello,there,donkey");

        // WHEN we try to replace with a token that is not present:
        tagList.replace("((non-existing-tag))", "someNewValue");

        // THEN nothing should have happened:
        assertEquals(3, tagList.size());
        assertTrue(tagList.hasTag("hello"));
        assertTrue(tagList.hasTag("there"));
        assertTrue(tagList.hasTag("donkey"));
        assertFalse(tagList.hasTag("someNewValue"));
    }

    @Test
    public void replace_withMatch_shouldReplace() {
        // GIVEN a tag with a token to be replaced:
        TagList tagList = TagList.of("hello,((replaceme)),there");

        // WHEN we do a token replacement:
        tagList.replace("((replaceme))", "done!");

        // THEN we should see the replacement worked:
        assertEquals("hello, there, done!", tagList.toString());
    }

    @Test
    public void isValidNonEmptyTagString_withNull_shouldReturnFalse() {
        // GIVEN a null tag string:
        String tagString = null;

        // WHEN we validate it:
        boolean result = TagList.isValidNonEmptyTagString(tagString);

        // THEN it should be invalid:
        assertFalse(result);
    }

    @Test
    public void isValidNonEmptyTagString_withBlankString_shouldReturnFalse() {
        // GIVEN blank tag strings:
        String[] blankInputs = {"", "   ", "\t", "\n", "  \t\n  "};

        for (String input : blankInputs) {
            // WHEN we validate each one:
            boolean result = TagList.isValidNonEmptyTagString(input);

            // THEN they should all be invalid:
            assertFalse(result, "Expected blank string '" + input + "' to be invalid");
        }
    }

    @Test
    public void isValidNonEmptyTagString_withValidSingleTag_shouldReturnTrue() {
        // GIVEN valid single tag strings:
        String[] validInputs = {"hello", "world", "tag123", "my-tag", "my_tag", "tag.name"};

        for (String input : validInputs) {
            // WHEN we validate each one:
            boolean result = TagList.isValidNonEmptyTagString(input);

            // THEN they should all be valid:
            assertTrue(result, "Expected valid tag '" + input + "' to be valid");
        }
    }

    @Test
    public void isValidNonEmptyTagString_withValidCommaSeparatedTags_shouldReturnTrue() {
        // GIVEN valid comma-separated tag strings:
        String[] validInputs = {
            "hello,world",
            "tag1,tag2,tag3",
            "my-tag,your-tag",
            "a,b,c,d,e"
        };

        for (String input : validInputs) {
            // WHEN we validate each one:
            boolean result = TagList.isValidNonEmptyTagString(input);

            // THEN they should all be valid:
            assertTrue(result, "Expected valid tags '" + input + "' to be valid");
        }
    }

    @Test
    public void isValidNonEmptyTagString_withLeftBrace_shouldReturnFalse() {
        // GIVEN tag strings containing left brace:
        String[] invalidInputs = {"{hello", "hel{lo", "hello{", "{", "tag1,{tag2"};

        for (String input : invalidInputs) {
            // WHEN we validate each one:
            boolean result = TagList.isValidNonEmptyTagString(input);

            // THEN they should all be invalid:
            assertFalse(result, "Expected tag with '{' character '" + input + "' to be invalid");
        }
    }

    @Test
    public void isValidNonEmptyTagString_withRightBrace_shouldReturnFalse() {
        // GIVEN tag strings containing right brace:
        String[] invalidInputs = {"}hello", "hel}lo", "hello}", "}", "tag1,tag2}"};

        for (String input : invalidInputs) {
            // WHEN we validate each one:
            boolean result = TagList.isValidNonEmptyTagString(input);

            // THEN they should all be invalid:
            assertFalse(result, "Expected tag with '}' character '" + input + "' to be invalid");
        }
    }

    @Test
    public void isValidNonEmptyTagString_withPipe_shouldReturnFalse() {
        // GIVEN tag strings containing pipe character:
        String[] invalidInputs = {"|hello", "hel|lo", "hello|", "|", "tag1,|tag2"};

        for (String input : invalidInputs) {
            // WHEN we validate each one:
            boolean result = TagList.isValidNonEmptyTagString(input);

            // THEN they should all be invalid:
            assertFalse(result, "Expected tag with '|' character '" + input + "' to be invalid");
        }
    }

    @Test
    public void isValidNonEmptyTagString_withMultipleDisallowedChars_shouldReturnFalse() {
        // GIVEN tag strings containing multiple disallowed characters:
        String[] invalidInputs = {"{hello}", "{tag1}|{tag2}", "a{b|c}d", "|||{{{}}}"};

        for (String input : invalidInputs) {
            // WHEN we validate each one:
            boolean result = TagList.isValidNonEmptyTagString(input);

            // THEN they should all be invalid:
            assertFalse(result, "Expected tag with multiple disallowed characters '" + input + "' to be invalid");
        }
    }
}