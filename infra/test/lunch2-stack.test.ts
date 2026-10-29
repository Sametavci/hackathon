import { App } from 'aws-cdk-lib';
import { Match, Template } from 'aws-cdk-lib/assertions';
import assert from 'node:assert/strict';
import { mkdtempSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { before, describe, it } from 'node:test';
import { Lunch2Stack } from '../lib/lunch2-stack.js';

function synth(region = 'eu-central-1'): Template {
  const appDir = mkdtempSync(join(tmpdir(), 'lunch2-app-'));
  writeFileSync(join(appDir, 'quarkus-run.jar'), 'fake jar');
  const stack = new Lunch2Stack(new App(), 'Lunch2', { appDir, env: { account: '123456789012', region } });
  return Template.fromStack(stack);
}

/** Rendered user-data script with CloudFormation tokens replaced by <Token>. */
function userData(template: Template): string {
  const instance = Object.values(template.findResources('AWS::EC2::Instance'))[0];
  const parts: unknown[] = instance.Properties.UserData['Fn::Base64']['Fn::Join'][1];
  return parts.map((p) => (typeof p === 'string' ? p : '<Token>')).join('');
}

describe('Lunch2Stack', () => {
  let template: Template;
  before(() => {
    template = synth();
  });

  it('creates exactly one free-tier t3.micro instance with IMDSv2', () => {
    template.resourceCountIs('AWS::EC2::Instance', 1);
    template.hasResourceProperties('AWS::EC2::Instance', { InstanceType: 't3.micro' });
    template.hasResourceProperties('AWS::EC2::LaunchTemplate', {
      LaunchTemplateData: { MetadataOptions: { HttpTokens: 'required' } },
    });
  });

  it('creates no resources that bill while idle besides the instance and its IP', () => {
    for (const type of [
      'AWS::EC2::NatGateway',
      'AWS::ElasticLoadBalancingV2::LoadBalancer',
      'AWS::RDS::DBInstance',
      'AWS::DynamoDB::Table',
      'AWS::SecretsManager::Secret',
      'AWS::EC2::VPCEndpoint',
    ]) {
      template.resourceCountIs(type, 0);
    }
  });

  it('opens only HTTP port 80 to the internet, no SSH', () => {
    template.hasResourceProperties('AWS::EC2::SecurityGroup', {
      SecurityGroupIngress: [{ CidrIp: '0.0.0.0/0', FromPort: 80, ToPort: 80, IpProtocol: 'tcp' }],
    });
    const groups = template.findResources('AWS::EC2::SecurityGroup');
    for (const sg of Object.values(groups)) {
      for (const rule of sg.Properties.SecurityGroupIngress ?? []) {
        assert.notEqual(rule.FromPort, 22, 'SSH must not be open');
      }
    }
    template.resourceCountIs('AWS::EC2::SecurityGroupIngress', 0);
  });

  it('attaches a fixed Elastic IP to the instance', () => {
    template.resourceCountIs('AWS::EC2::EIP', 1);
    template.hasResourceProperties('AWS::EC2::EIPAssociation', {
      AllocationId: { 'Fn::GetAtt': [Match.stringLikeRegexp('^PublicIp'), 'AllocationId'] },
      InstanceId: { Ref: Match.stringLikeRegexp('^Server') },
    });
  });

  it('uses a public-subnet-only VPC in one AZ', () => {
    template.resourceCountIs('AWS::EC2::Subnet', 1);
    template.hasResourceProperties('AWS::EC2::Subnet', { MapPublicIpOnLaunch: true });
  });

  it('runs the app on port 80 with an English JVM locale and Java 21', () => {
    const script = userData(template);
    assert.match(script, /dnf install -y java-21-amazon-corretto-headless/);
    assert.match(script, /QUARKUS_HTTP_PORT=80/);
    assert.match(script, /AmbientCapabilities=CAP_NET_BIND_SERVICE/);
    assert.match(script, /-Duser\.language=en -jar \/opt\/lunch2\/app\/quarkus-run\.jar/);
    assert.match(script, /LUNCH2_PUBLIC_BASE_URL=http:\/\/<Token>/);
    assert.match(script, /systemctl enable --now lunch2/);
  });

  it('reads secrets from SSM at boot and never traces them into the console log', () => {
    const script = userData(template);
    assert.match(script, /aws ssm get-parameter --region \$REGION --name \/lunch2\/admin-key --with-decryption/);
    assert.match(script, /--name \/lunch2\/anthropic-api-key/);
    assert.doesNotMatch(script, /set -[a-z]*x/, 'set -x would print secrets to the EC2 console output');
    assert.doesNotMatch(script, /sk-ant-/, 'no API key literal in the template');
    assert.match(script, /chmod 600 \/opt\/lunch2\/env/);
  });

  it('scopes the instance SSM permission to /lunch2/* parameters', () => {
    template.hasResourceProperties('AWS::IAM::Policy', {
      PolicyDocument: {
        Statement: Match.arrayWith([
          Match.objectLike({
            Action: 'ssm:GetParameter',
            Resource: {
              'Fn::Join': ['', Match.arrayWith([Match.stringLikeRegexp(':parameter/lunch2/\\*$')])],
            },
          }),
        ]),
      },
    });
  });

  it('replaces the instance when the app bundle changes', () => {
    const instance = Object.values(template.findResources('AWS::EC2::Instance'))[0];
    const launchTemplates = template.findResources('AWS::EC2::LaunchTemplate');
    assert.ok(instance && Object.keys(launchTemplates).length === 1);
    const logicalIds = Object.keys(template.findResources('AWS::EC2::Instance'));
    // userDataCausesReplacement appends a hash of the user data to the logical ID.
    assert.match(logicalIds[0], /^Server[0-9A-F]+[0-9a-f]{16}$/);
  });

  it('puts the deploy region into the user data', () => {
    assert.match(userData(synth('eu-west-1')), /REGION=eu-west-1/);
    assert.match(userData(template), /REGION=eu-central-1/);
  });

  it('outputs the URL, the IP and an SSM shell command', () => {
    template.hasOutput('Url', {});
    template.hasOutput('PublicIpAddress', {});
    template.hasOutput('ShellCommand', {});
  });
});
