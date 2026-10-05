import http from 'k6/http';
import { check } from 'k6';
import exec from 'k6/execution';
import { Counter, Trend } from 'k6/metrics';
import { htmlReport } from 'https://raw.githubusercontent.com/benc-uk/k6-reporter/3.0.4/dist/bundle.js';

const fixture = JSON.parse(open(__ENV.SCIM_FIXTURE));
const { users, warmups, iterations, pageSize, mode } = fixture;
const expectedIds = new Set(fixture.expectedIds);
const targetIndex = Math.floor(users / 2);
const target = `benchmark-${String(targetIndex).padStart(7, '0')}@telemark.no`;
const cases = [
    { name: 'first_page', total: users },
    { name: 'deep_page', start: users - 99, total: users },
    { name: 'count_only', count: 0, total: users },
    { name: 'username_eq', filter: `userName eq "${target}"`, total: 1 },
    { name: 'external_id_eq', filter: `externalId eq "benchmark-${targetIndex}"`, total: 1 },
    { name: 'email_value_eq', filter: `emails.value eq "${target}"`, total: 1 },
    { name: 'email_type_filter', filter: `emails[type eq "work" and value eq "${target}"]`, total: 1 },
    { name: 'email_primary_filter', filter: `emails[primary eq true and value eq "${target}"]`, total: 1 },
    { name: 'substring', filter: 'userName co "benchmark-"', total: users },
    { name: 'active', filter: 'active eq true', total: Math.ceil(users / 2) },
    { name: 'role_eq', filter: 'roles.value eq "reader"', total: users },
    { name: 'no_match', filter: 'userName eq "missing@telemark.no"', total: 0 },
];
const metricNames = mode === 'searches'
    ? cases.map(c => c.name)
    : ['page', 'traversal_requests', 'traversal_elapsed'];
const trends = Object.fromEntries(metricNames.map(name => [name, new Trend(`scim_${name}_ms`, true)]));
const completed = new Counter('scim_completed_samples');

export const options = {
    scenarios: {
        benchmark: {
            executor: 'shared-iterations', vus: 1, iterations: warmups + iterations,
            maxDuration: '1h', gracefulStop: '30s',
        },
    },
    thresholds: {
        checks: ['rate==1'],
        http_req_failed: ['rate==0'],
        scim_completed_samples: [`count==${iterations}`],
    },
    summaryTrendStats: ['min', 'med', 'p(95)', 'max'],
};

let token;
let tokenExpiresAt = 0;

function verify(condition, message) {
    if (!check(condition, { [message]: value => value })) exec.test.abort(message);
}

function accessToken() {
    if (Date.now() >= tokenExpiresAt) {
        const response = http.get(fixture.tokenUrl, { tags: { name: 'auth' } });
        verify(response.status === 200, 'token response is successful');
        const body = response.json();
        verify(typeof body.access_token === 'string', 'token is present');
        token = body.access_token;
        tokenExpiresAt = Date.now() + Math.max(0, (body.expires_in || 60) - 10) * 1000;
    }
    return token;
}

function page(name, start, count, filter, cursor) {
    let url = cursor === undefined
        ? `${fixture.baseUrl}/Users?startIndex=${start}&count=${count}&sortBy=userName&sortOrder=ascending`
        : `${fixture.baseUrl}/Users?cursor=${encodeURIComponent(cursor)}&count=${count}`;
    if (filter) url += `&filter=${encodeURIComponent(filter)}`;
    const response = http.get(url, {
        headers: { Authorization: `Bearer ${accessToken()}`, Accept: 'application/scim+json' },
        tags: { name },
    });
    verify(response.status === 200, `${name}: HTTP 200`);
    return { body: response.json(), duration: response.timings.duration };
}

export default function () {
    const measured = exec.scenario.iterationInTest >= warmups;
    if (mode === 'searches') {
        for (const scenario of cases) {
            const start = scenario.start ?? 1;
            const count = scenario.count ?? 100;
            const { body, duration } = page(scenario.name, start, count, scenario.filter);
            verify(body.totalResults === scenario.total, `${scenario.name}: totalResults`);
            const resources = body.Resources || [];
            verify(resources.length === Math.min(count, Math.max(0, scenario.total - start + 1)), `${scenario.name}: page size`);
            if (scenario.total === 1) verify(resources[0].userName === target, `${scenario.name}: matching user`);
            if (measured) trends[scenario.name].add(duration);
        }
    } else {
        const seen = new Set();
        const started = Date.now();
        let requestTime = 0;
        let cursor = '';
        const cursors = new Set();
        do {
            const { body, duration } = page('traversal', undefined, pageSize, undefined, cursor);
            verify(body.totalResults === users, 'traversal: totalResults');
            const resources = body.Resources || [];
            verify(resources.length > 0 && resources.length <= pageSize, 'traversal: page size');
            for (const user of resources) {
                verify(expectedIds.has(user.id) && !seen.has(user.id), 'traversal: expected unique user');
                seen.add(user.id);
            }
            requestTime += duration;
            if (measured) trends.page.add(duration);
            cursor = body.nextCursor;
            if (cursor != null) {
                verify(typeof cursor === 'string' && cursor.length > 0 && !cursors.has(cursor), 'traversal: advancing cursor');
                cursors.add(cursor);
                verify(seen.size < users, 'traversal: no cursor after final user');
            }
        } while (cursor != null);
        verify(seen.size === expectedIds.size, 'traversal: all seeded users returned');
        if (measured) {
            trends.traversal_requests.add(requestTime);
            trends.traversal_elapsed.add(Date.now() - started);
        }
    }
    if (measured) completed.add(1);
}

export function handleSummary(data) {
    const lines = ['scenario,users,warmups,iterations,min_ms,median_ms,p95_ms,max_ms'];
    for (const name of metricNames) {
        const values = data.metrics[`scim_${name}_ms`]?.values;
        if (values) lines.push([name, users, warmups, iterations, values.min, values.med, values['p(95)'], values.max].join(','));
    }
    const csv = `${lines.join('\n')}\n`;
    return {
        'report.html': htmlReport(data, { title: `SCIM ${mode} - ${users} users` }),
        'summary.json': JSON.stringify(data, null, 2),
        'results.csv': csv,
        stdout: csv,
    };
}
