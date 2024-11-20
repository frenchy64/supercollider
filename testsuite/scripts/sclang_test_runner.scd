// this script is meant to be run with four arguments:
// 1. path to test prototype indicating tests to be run
// - default: "./test_run_proto_gha.scd"
// 2. path to test result
// - default: "./test_result.scxtar"
// 3. which partition of the tests should be run (zero based)
// - default: 0
// 4. the number of test partitions
// - default: 1 (must provide if partition provided)
(
var thisClump, nClumps, nargv = thisProcess.argv.size;
~exitWhenDone = nargv > 0;
try {
if(nargv > 0) {
	"passed args: %".format(thisProcess.argv).postln;
	~testProto = thisProcess.argv[0];
	~testResult = thisProcess.argv[1];
	if(nargv > 2) {
		thisClump = thisProcess.argv[2].asInteger();
		nClumps = thisProcess.argv[3].asInteger();
	};
} {
	~testProto = PathName(thisProcess.nowExecutingPath).pathOnly +/+ "test_run_proto_gha.scd";
	// ~testProto = PathName(thisProcess.nowExecutingPath).pathOnly +/+ "test_run_proto_all.scd"; // for testing
	~testResult = PathName(thisProcess.nowExecutingPath).pathOnly +/+ "run" +/+ "test_result.scxtar";
};

thisClump ?? {
  if(nClumps.notNil) {
    // this should be unreachable
    Error("thisClump and nClumps and must set together").throw
  };
	thisClump = 0;
	nClumps = 1;
};

if((0>thisClump) || (thisClump>=nClumps)) {
	Error("Invalid clumps! % %".format(thisClump, nClumps)).throw
};

if(File.exists(~testProto).not) {
	Error("Test prototype file doesn't exist at %".format(~testProto)).throw
};

"~testProto path: %".format(~testProto).postln;
"~testResult path: %".format(~testResult).postln;

if(nClumps > 1) {
	"dividing test suite into % clumps and testing clump index %".format(nClumps, thisClump).postln;
};

// FIXME !!!!!!! set to false !!!!!!!!
~verboseSetup = true; // true or false
// FIXME !!!!!!! set to false !!!!!!!!

~useColors = \Document.asClass.isNil; // use colors if we're running outside of the IDE

~makeBold = {|str|
	if(~useColors) {
		str = "%[1m%%[0m".format(27.asAscii, str, 27.asAscii);
	};
	str;
};

~writeResult = {|results, path|
	results.writeArchive(path);
};

// extract the test record dictionary from the input file
~getTestRecord = {|file|
	var test_record, test_record_string;
	var success = false;

	test_record_string = File(file, "r").readAllString();

	test_record = test_record_string.interpret();

	test_record;
};


// expand { suite: * } to a dictionary of { test: * } records
~expandSuiteGlob = { |testsDict|
	var all;
	var suiteGlob = testsDict.select { |item| item[\suite] == "*" };

	if (suiteGlob.notEmpty) {
		all = UnitTest.allTestClasses.keys.select({ |t| t != "...All..." }).asArray.collect({ |className|
			Dictionary.newFrom((\suite: "Test" ++ className, \test: "*"))
		});
		testsDict.removeAll(suiteGlob);
		testsDict = all ++ testsDict;
	};

	testsDict;
};

~selectThisTestClump = {|tests|
  var sortedTests, nTests, minTestsPerClump, extraTestsToDistribute, groupSizes,
    clumps, actualNClumps;

  // first sort tests for predictable test partitioning
  sortedTests = tests.as(Array).sort({
    |a,b|
    if((a[\suite]==b[\suite]) && a[\test]==(b[\test])) {
      Error("Duplicate tests! % %".format(a, b)).throw
    };
    (a[\suite]<b[\suite]) && (a[\test]<b[\test])
  });

  nTests = sortedTests.size();
  minTestsPerClump = (nTests/nClumps).asInteger;

  // now we partition the tests. each partition will have
  // minTestsPerClump or minTestsPerClump+1 tests in it, with
  // larger partitions occuring first.
  extraTestsToDistribute = nTests-(minTestsPerClump*nClumps);
  if (extraTestsToDistribute>nClumps) {
    Error("Too many tests left over to distribute evenly").throw;
  };
  groupSizes = Array.fill(nClumps, minTestsPerClump);
  extraTestsToDistribute.do({|i|
    groupSizes[i] = groupSizes[i]+1;
  });
  clumps = sortedTests.clumps(groupSizes);

  // double check that we won't miss any tests, assuming other jobs
  // will pick the the other clumps.
  actualNClumps = clumps.size();
  if (actualNClumps > nClumps) {
	  Error("Partitioned tests into too many clumps! Expected %, actual %.".format(nClumps, actualNClumps)).throw
	};
  if (tests.as(Set) != clumps.flatten.as(Set)) {
	  Error("Partitioning tests is a lossy operation!").throw
  };

  // if the number of tests is less than nClumps, there will
  // exist clumps in index 0..(nClumps-1) that will be nil.
	clumps.at(thisClump) ?? #[];
};

~testrun = {|protoFile, resultFile|
	var tests = ~getTestRecord.(protoFile);
	var skipped, toExpand, toExclude;
	var currentSuite = "";

	UnitTest.findTestClasses();

	tests = ~expandSuiteGlob.(tests);

	tests = List.newFrom(tests.collect(Dictionary.newFrom(_)));

	// Expand *'s
	toExpand = tests.select({|t| (t[\test] == "*") && (t[\completed] != true) });
	~verboseSetup.if({"Expanding %\n".postf(toExpand)});
	toExpand.do {|wildcardTest|
		var allTests, newTest, class;

		class = wildcardTest[\suite].asSymbol.asClass;

		if (class.respondsTo(\findTestMethods).not && class.notNil) {
			class = ("Test" ++ class.name.asString).asSymbol.asClass;
		};

		if (class.isNil) {
			wildcardTest[\error] = "Class % not found".format(class);
			wildcardTest[\completed] = true;
			// ~writeResult.(test_record, file);
		} {
			~verboseSetup.if({"class: %".format(class).postln});
			class.tryPerform(\findTestMethods).do {|testMethod|
				~verboseSetup.if({"method: %".format(testMethod.name).postln});
				newTest = Dictionary.newFrom((
					\suite: class.name.asString,
					\test: testMethod.name,
					\skip: wildcardTest[\skip],
					\skipReason: wildcardTest[\skipReason],
				));

				tests.add(newTest);
				~verboseSetup.if({"tests.size: %".format(tests.size).postln});
			};
			tests.remove(wildcardTest);
			// ~writeResult.(test_record, file);
		}
	};

	// Ensure excluded tests are not run
	toExclude = tests.select({|t|
		if(t.isKindOf(Set)) { // sometimes we don't get a dictionary here... it shouldn't be the case, but ?
			if (t[\skip].notNil) {
				t[\skip] = (t[\skip].asString.toLower == "true")
			};

			t[\skip] == true
		} {
			"detected a non-dictionary entry: %. Skipping...".format(t.asCompileString).warn;
			Dictionary.newFrom([
				\test: \error
			])[\skip] == true;
		}
	});

	~verboseSetup.if({"Excluding: %".format(toExclude.join(", ")).postln});
	tests = tests.reject({|t|
		if(t.isKindOf(Set)) { // sometimes we don't get a dictionary here... it shouldn't be the case, but ?
			(toExclude.detect({|excluded|
				(t[\suite].asString == excluded[\suite].asString)
				&& (t[\test].asString == excluded[\test].asString)
				&& (t !== excluded)
			}).size > 0)
		} {
			"detected a non-dictionary entry: %. Rejecting...".format(t.asCompileString).warn;
			true;
		}
	});
	// test_record["tests"] = tests;
	// ~writeResult.(tests, file);
	~verboseSetup.if({"After exclude: ".post; tests.do(_.postln)});

	tests = ~selectThisTestClump.(tests);

	~verboseSetup.if({"After clump: ".post; tests.do(_.postln)});

	"\n\n\t*** Running the tests ***\n\n".post;

	// Okay, time to run the tests
	tests.do {|test|
		var class, testname, script, result,
		oldFailures, oldPasses, newPasses, newFailures,
		startTime, endTime,
		success = false;

		if(currentSuite != test[\suite]) {
			"\n\n\tSuite: %\n".postf(~makeBold.(test[\suite]));
			currentSuite = test[\suite];
		};

		"\n\tTest: %\n".postf(~makeBold.(test[\test]));

		if (test[\completed].isNil) {
			if (test[\skip] == true) {
				test[\completed] = true;
				test[\attemptsRemaining] = nil;
				~writeResult.(tests, resultFile);
			} {
				test[\completed] = false;
				class = test[\suite].asSymbol.asClass;
				testname = test[\test].asSymbol;
				if (class.isNil) {
					test[\error] = "Class % not found".format(class);
					test[\completed] = true;
					~writeResult.(tests, resultFile);
				} {
					class.findTestMethods();
					class.setUpClass();

					script = class.findTestMethods().detect({ |m| m.name == testname });
					if (script.notNil) {
						// This is a bad way to get the results, but there's no other for now.
						// One this is working end-to-end, then UnitTest can be improved incrementally.
						oldPasses = IdentitySet.newFrom(class.passes);
						oldFailures = IdentitySet.newFrom(class.failures);

						// RUN THE TEST ///////////////
						~writeResult.(tests, resultFile);
						startTime = Date.localtime();

						try {
							result = class.new.runTestMethod(script);
						} { |error|
							class.new.failed(script, error.errorString, true, nil);
						};

						endTime = Date.localtime();
						test[\completed] = true;
						test[\attemptsRemaining] = nil;
						test[\duration] = endTime.rawSeconds - startTime.rawSeconds;
						///////////////////////////////

						newPasses = IdentitySet.newFrom(class.passes).difference(oldPasses);
						newFailures = IdentitySet.newFrom(class.failures).difference(oldFailures);
						test[\results] = List();
						newPasses.do {|pass|
							test[\results].add((
								\test: ("" ++ pass.message)[0..1000],
								\pass: true,
							))
						};
						newFailures.do {|fail|
							test[\results].add((
								\pass: false,
								\test: ("" ++ fail.message.split($\n)[0])[0..1000],
								\reason: (fail.message.split($\n)[1..])[0..1000]
							))
						};

						~writeResult.(tests, resultFile);
					} {
						test[\error] = "Test not found.";
						test[\completed] = true;
						~writeResult.(tests, resultFile);
					};

					class.tearDownClass();
				}
			}
		};
	}
};


{
	~testrun.(~testProto, ~testResult);
	"\n\n******** DONE ********\n\n".postln;
	// 0.exit;
	if(~exitWhenDone) {
		0.exit;
    this.halt;
	}; // exit only if the script was called with arguments
}.forkIfNeeded(AppClock);
} { |error| //catch
  if(~exitWhenDone) {
    error.errorString.postln;
    2.exit;
    this.halt;
  } {
    error.throw;
  };
};
)
